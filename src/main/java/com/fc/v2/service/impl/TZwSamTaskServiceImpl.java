package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.mapper.auto.TSysDepartmentMapper;
import com.fc.v2.mapper.auto.TZwSamPlanMapper;
import com.fc.v2.mapper.auto.TZwSamTaskMapper;
import com.fc.v2.mapper.auto.TZwSourceMapper;
import com.fc.v2.model.auto.TSysDepartment;
import com.fc.v2.model.auto.TZwSamPlan;
import com.fc.v2.model.auto.TZwSamTask;
import com.fc.v2.model.auto.TZwSource;
import com.fc.v2.service.ITZwSamTaskService;
import com.fc.v2.service.zw.DispatchWindow;
import com.fc.v2.service.zw.SamDateRule;
import com.fc.v2.service.zw.SamRunResult;
import com.fc.v2.service.zw.TeamBalancer;

/**
 * 采样任务到期派单 Service业务层处理（scheduling-job 形状：周期执行，无人工抄录入口）。
 *
 * <p>翻单（{@link #listDue}）与回写（{@link #runOnce}）共用 {@link SamDateRule} 这一套口径，
 * 谁也不许另写一把尺。整轮不加事务：单条失败只记一笔 itemErrors 后跳过，
 * 卡住一条不许把整轮回滚报废；每条状态翻转各自走条件更新，本身即原子。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TZwSamTaskServiceImpl implements ITZwSamTaskService {

    /** 条目情形 0待派 1已派 2挂起 3撤单 4转作他理 */
    private static final int STATUS_WAIT = 0;
    private static final int STATUS_DISPATCHED = 1;
    private static final int STATUS_HUNG = 2;
    private static final int STATUS_CANCEL = 3;
    private static final int STATUS_TRANSFER = 4;

    /** 底档/计划现行、点位在用、班组在岗 */
    private static final int ACTIVE = 0;
    private static final int SOURCE_RETIRED = 1;
    private static final int DEPT_ON_DUTY = 1;

    private static final String ITEM_DAY_PATTERN = "yyyyMMdd";

    @javax.annotation.Resource
    private TZwSamTaskMapper zwSamTaskMapper;
    @javax.annotation.Resource
    private TZwSamPlanMapper zwSamPlanMapper;
    @javax.annotation.Resource
    private TZwSourceMapper zwSourceMapper;
    @javax.annotation.Resource
    private TSysDepartmentMapper sysDepartmentMapper;
    @javax.annotation.Resource
    private DispatchWindow dispatchWindow;

    @Override
    public TZwSamTask selectTZwSamTaskById(Long id) {
        return this.zwSamTaskMapper.selectById(id);
    }

    @Override
    public List<TZwSamTask> selectTZwSamTaskList(com.baomidou.mybatisplus.core.conditions.Wrapper<TZwSamTask> queryWrapper) {
        return this.zwSamTaskMapper.selectList(queryWrapper);
    }

    /**
     * 翻单：已浮出待办、且尚未过约定终结时刻的待派条目。
     * 浮出/过期一律走 {@link SamDateRule} 谓词，与 {@link #runOnce} 回写同一把尺；窗口不在此把关。
     */
    @Override
    public List<TZwSamTask> listDue(Date at) {
        List<TZwSamTask> waiting = this.zwSamTaskMapper.selectList(new QueryWrapper<TZwSamTask>()
                .eq("status", STATUS_WAIT)
                .eq("del_flag", 0));
        List<TZwSamTask> due = new ArrayList<TZwSamTask>();
        for (TZwSamTask r : waiting) {
            if (r.getDueAt() == null) {
                continue;
            }
            // 同一套讲法：浮出含边界，过期 at > dueAt（等于不算过）
            if (SamDateRule.surfaced(r.getDueAt(), r.getAmount(), at)
                    && !SamDateRule.overdue(r.getDueAt(), at)) {
                due.add(r);
            }
        }
        return due;
    }

    /**
     * 按检测计划周期幂等生成该浮出的事项条。
     * 从首个采样日起逐周期顺排，凡在 at 已浮出的周期各落一条；同计划同点位同约定日已存在则不重复落。
     * 事项编号、点位代号、约定日期全部从底档带入，不经人手抄录。
     */
    @Override
    public int generate(Date at) {
        List<TZwSamPlan> plans = this.zwSamPlanMapper.selectList(new QueryWrapper<TZwSamPlan>()
                .eq("status", ACTIVE)
                .eq("del_flag", 0)
                .isNotNull("first_due")
                .gt("period_days", 0));
        int generated = 0;
        for (TZwSamPlan plan : plans) {
            generated += generatePlan(plan, at);
        }
        return generated;
    }

    private int generatePlan(TZwSamPlan plan, Date at) {
        int created = 0;
        BigDecimal amount = plan.getAmount() == null ? BigDecimal.ZERO : plan.getAmount();
        Calendar c = Calendar.getInstance();
        c.setTime(SamDateRule.endOfDay(plan.getFirstDue()));
        int periodDays = plan.getPeriodDays();
        // 从首个采样日顺排：浮出一个补一个，漏跑的周期照样补账；遇到还没浮出的周期即停（周期为正，必然收敛）
        for (int k = 0; ; k++) {
            Date dueAt = c.getTime();
            if (!SamDateRule.surfaced(dueAt, amount, at)) {
                break;
            }
            if (inPlanEffect(plan, dueAt)) {
                created += insertOne(plan, amount, dueAt);
            }
            c.add(Calendar.DAY_OF_MONTH, periodDays);
        }
        return created;
    }

    /** 约定日须落在计划生效区间 [eff_start, eff_end) 内 */
    private boolean inPlanEffect(TZwSamPlan plan, Date dueAt) {
        if (plan.getEffStart() != null && SamDateRule.epochSecond(dueAt) < SamDateRule.epochSecond(plan.getEffStart())) {
            return false;
        }
        if (plan.getEffEnd() != null && SamDateRule.epochSecond(dueAt) >= SamDateRule.epochSecond(plan.getEffEnd())) {
            return false;
        }
        return true;
    }

    private int insertOne(TZwSamPlan plan, BigDecimal amount, Date dueAt) {
        String itemNo = plan.getPlanNo() + "-" + new SimpleDateFormat(ITEM_DAY_PATTERN).format(dueAt);
        Integer exists = this.zwSamTaskMapper.selectCount(new QueryWrapper<TZwSamTask>()
                .eq("item_no", itemNo)
                .eq("del_flag", 0));
        if (exists != null && exists > 0) {
            return 0;
        }
        TZwSamTask r = new TZwSamTask();
        r.setItemNo(itemNo);
        r.setPlanNo(plan.getPlanNo());
        r.setSiteNo(plan.getSiteNo());
        r.setTeamId(plan.getTeamId());
        r.setDueAt(dueAt);
        r.setAmount(amount);
        r.setContent(plan.getContent());
        r.setStatus(STATUS_WAIT);
        r.setDelFlag(0);
        try {
            return this.zwSamTaskMapper.insert(r);
        } catch (DataIntegrityViolationException e) {
            // 并发另一轮已落同号事项（uk_sam_item_no / uk_sam_plan_site_due），幂等视作非新增
            return 0;
        }
    }

    /**
     * 跑一轮：
     * <ol>
     *   <li>窗口之外（夜间/法定节假日/汛期段外）→ SILENT，整段静默，什么都不动；</li>
     *   <li>窗口内先按周期生成事项条，再捞本轮候选（已浮出的待派条目，含已过期待处置者）；</li>
     *   <li>无候选 → IDLE（"本轮无活"），与 ERROR 严格分开；</li>
     *   <li>逐条处置：开单前点位退役/直挂班组停班/无班可派 → 挂起；已过期只等撤单或转理，不再派出；
     *       其余按紧急优先、加权负载派出；单条失败跳过；</li>
     *   <li>轮末对候选 id 集做一次 GROUP BY 现数，屏上三数与库内同出此次计数。</li>
     * </ol>
     */
    @Override
    public SamRunResult runOnce(Date at) {
        if (at == null) {
            return SamRunResult.error("执行时刻为空");
        }
        if (!this.dispatchWindow.inWindow(at)) {
            String note = this.dispatchWindow.isHoliday(at)
                    ? "法定节假日整段静默，原地等下一轮"
                    : "窗口之外（夜间/非值班时段/汛期段外），本轮静默，原地等下一轮";
            return SamRunResult.silent(note);
        }

        int generated;
        List<TZwSamTask> candidates;
        Map<Long, TSysDepartment> teamMap;
        try {
            generated = generate(at);
            candidates = listCandidates(at);
            teamMap = loadTeams();
        } catch (Exception e) {
            return SamRunResult.error("轮次准备失败：" + e.getMessage());
        }
        if (candidates.isEmpty()) {
            return SamRunResult.idle();
        }

        Set<String> retiredSites = loadRetiredSites(candidates);
        TeamBalancer balancer = new TeamBalancer(loadCurrentDispatched(teamMap.keySet()));
        List<String> itemErrors = new ArrayList<String>();

        // 紧急（浮出超三天未派）在前，约定日早的在前；普通事项不许插队到紧急事项之前
        List<TZwSamTask> ordered = new ArrayList<TZwSamTask>(candidates);
        Collections.sort(ordered, new Comparator<TZwSamTask>() {
            @Override
            public int compare(TZwSamTask a, TZwSamTask b) {
                boolean ua = SamDateRule.urgent(a.getDueAt(), a.getAmount(), at);
                boolean ub = SamDateRule.urgent(b.getDueAt(), b.getAmount(), at);
                if (ua != ub) {
                    return ua ? -1 : 1;
                }
                return Long.compare(SamDateRule.epochSecond(a.getDueAt()), SamDateRule.epochSecond(b.getDueAt()));
            }
        });

        List<TSysDepartment> activeTeams = new ArrayList<TSysDepartment>();
        for (TSysDepartment d : teamMap.values()) {
            if (isOnDuty(d)) {
                activeTeams.add(d);
            }
        }

        for (TZwSamTask item : ordered) {
            try {
                handleOne(item, at, retiredSites, teamMap, activeTeams, balancer);
            } catch (Exception e) {
                // 卡住一条只记一笔，不拖垮整轮
                itemErrors.add(item.getItemNo() + "：" + e.getMessage());
            }
        }

        return finishRound(generated, candidates, itemErrors);
    }

    /** 本轮候选：已浮出的待派条目（含已过期者——过期的只等撤单/转理，仍要在本轮账上） */
    private List<TZwSamTask> listCandidates(Date at) {
        List<TZwSamTask> waiting = this.zwSamTaskMapper.selectList(new QueryWrapper<TZwSamTask>()
                .eq("status", STATUS_WAIT)
                .eq("del_flag", 0));
        List<TZwSamTask> candidates = new ArrayList<TZwSamTask>();
        for (TZwSamTask r : waiting) {
            if (r.getDueAt() != null && SamDateRule.surfaced(r.getDueAt(), r.getAmount(), at)) {
                candidates.add(r);
            }
        }
        return candidates;
    }

    private void handleOne(TZwSamTask item, Date at, Set<String> retiredSites,
                           Map<Long, TSysDepartment> teamMap, List<TSysDepartment> activeTeams,
                           TeamBalancer balancer) {
        // 期过了还没派出去的，只能撤单或转作他理——本轮任何自动动作（含挂起、派出）都不再做，原样留在待派账上
        if (SamDateRule.overdue(item.getDueAt(), at)) {
            return;
        }
        // 开单前先看点位：已退役（水源枯竭/达使用年限）就地挂起
        if (retiredSites.contains(item.getSiteNo())) {
            hang(item, "点位已退役（水源枯竭或达使用年限）", at);
            return;
        }

        Long directTeam = item.getTeamId();
        if (directTeam != null) {
            // 直挂班组整班停班 → 挂起，不另派别班
            TSysDepartment dept = teamMap.get(directTeam);
            if (dept == null || !isOnDuty(dept)) {
                hang(item, "承接班组整班停班", at);
                return;
            }
            dispatch(item, directTeam, teamName(dept), at);
            return;
        }

        // 不直挂的按当前负载加权分派；一个在岗班组都没有 → 挂起等下轮
        if (activeTeams.isEmpty()) {
            hang(item, "无班可派（全部班组停班）", at);
            return;
        }

        TSysDepartment picked = balancer.pick(activeTeams);
        if (picked == null) {
            hang(item, "无班可派（全部班组停班）", at);
            return;
        }
        if (dispatch(item, picked.getId(), teamName(picked), at)) {
            balancer.assigned(picked.getId());
        }
    }

    /**
     * 条件翻转挂起：必须仍是待派。先前已落地（已派/已挂/已撤）的那条原样保留，0 行不算成也不报错。
     */
    private void hang(TZwSamTask item, String reason, Date at) {
        this.zwSamTaskMapper.update(null, new UpdateWrapper<TZwSamTask>()
                .set("status", STATUS_HUNG)
                .set("hang_reason", reason)
                .set("update_time", at)
                .eq("id", item.getId())
                .eq("status", STATUS_WAIT)
                .isNull("dispatch_at")
                .eq("del_flag", 0));
    }

    /**
     * 条件派出：status 翻已派与 dispatch_at 钉库在同一条更新里一次完成，缺一样都不算成；
     * WHERE status=0 AND dispatch_at IS NULL 同时兜住"当日二次进来只认第一次落地""下轮不再当未派捞出"。
     * @return 是否本轮真正派出
     */
    private boolean dispatch(TZwSamTask item, Long teamId, String teamName, Date at) {
        String note = item.getContent() == null ? "" : item.getContent();
        String content = note + "｜受派班组：" + teamName;
        int rows = this.zwSamTaskMapper.update(null, new UpdateWrapper<TZwSamTask>()
                .set("status", STATUS_DISPATCHED)
                .set("team_id", teamId)
                .set("dispatch_at", at)
                .set("content", content)
                .set("update_time", at)
                .eq("id", item.getId())
                .eq("status", STATUS_WAIT)
                .isNull("dispatch_at")
                .eq("del_flag", 0));
        return rows > 0;
    }

    /**
     * 轮末收口：对本轮候选 id 集做一次 GROUP BY 现数——屏上待派/已派/挂起三数与库内现数同出此次计数。
     */
    private SamRunResult finishRound(int generated, List<TZwSamTask> candidates, List<String> itemErrors) {
        List<Long> ids = new ArrayList<Long>();
        for (TZwSamTask r : candidates) {
            ids.add(r.getId());
        }
        Map<Integer, Integer> now = new HashMap<Integer, Integer>();
        List<Map<String, Object>> rows = this.zwSamTaskMapper.selectMaps(
                new QueryWrapper<TZwSamTask>()
                        .select("status, count(*) as cnt")
                        .in("id", ids)
                        .eq("del_flag", 0)
                        .groupBy("status"));
        for (Map<String, Object> row : rows) {
            Object st = row.get("status");
            Object cnt = row.get("cnt");
            if (st != null && cnt != null) {
                now.put(((Number) st).intValue(), ((Number) cnt).intValue());
            }
        }
        int waiting = n(now.get(STATUS_WAIT));
        int dispatched = n(now.get(STATUS_DISPATCHED));
        int hung = n(now.get(STATUS_HUNG));
        return new SamRunResult(SamRunResult.Outcome.DISPATCHED, null, generated,
                waiting, dispatched, hung, itemErrors);
    }

    private static int n(Integer v) {
        return v == null ? 0 : v;
    }

    private Set<String> loadRetiredSites(List<TZwSamTask> candidates) {
        Set<String> siteNos = new HashSet<String>();
        for (TZwSamTask r : candidates) {
            if (r.getSiteNo() != null) {
                siteNos.add(r.getSiteNo());
            }
        }
        if (siteNos.isEmpty()) {
            return Collections.emptySet();
        }
        List<TZwSource> sources = this.zwSourceMapper.selectList(new QueryWrapper<TZwSource>()
                .in("site_no", siteNos)
                .eq("del_flag", 0));
        Set<String> retired = new HashSet<String>();
        for (TZwSource s : sources) {
            if (s.getStatus() != null && s.getStatus() == SOURCE_RETIRED) {
                retired.add(s.getSiteNo());
            }
        }
        return retired;
    }

    private Map<Long, TSysDepartment> loadTeams() {
        List<TSysDepartment> all = this.sysDepartmentMapper.selectList(new QueryWrapper<TSysDepartment>());
        Map<Long, TSysDepartment> map = new HashMap<Long, TSysDepartment>();
        for (TSysDepartment d : all) {
            map.put(d.getId(), d);
        }
        return map;
    }

    /** 班组当前负载：名下已派（未结）事项条数；负载越高权重越低 */
    private Map<Long, Integer> loadCurrentDispatched(Set<Long> teamIds) {
        Map<Long, Integer> load = new HashMap<Long, Integer>();
        if (teamIds.isEmpty()) {
            return load;
        }
        List<Map<String, Object>> rows = this.zwSamTaskMapper.selectMaps(
                new QueryWrapper<TZwSamTask>()
                        .select("team_id, count(*) as cnt")
                        .eq("status", STATUS_DISPATCHED)
                        .eq("del_flag", 0)
                        .in("team_id", teamIds)
                        .groupBy("team_id"));
        for (Map<String, Object> row : rows) {
            Object team = row.get("team_id");
            Object cnt = row.get("cnt");
            if (team != null && cnt != null) {
                load.put(((Number) team).longValue(), ((Number) cnt).intValue());
            }
        }
        return load;
    }

    /** 部门 status=1 在岗（与系统部门页"启用"开关同口径），其余视作整班停班 */
    private static boolean isOnDuty(TSysDepartment dept) {
        return dept.getStatus() != null && dept.getStatus() == DEPT_ON_DUTY;
    }

    private static String teamName(TSysDepartment dept) {
        return dept.getDeptName() == null ? String.valueOf(dept.getId()) : dept.getDeptName();
    }

    @Override
    public boolean cancel(Long id, String reason, Date at) {
        TZwSamTask r = this.zwSamTaskMapper.selectById(id);
        if (r == null || (r.getStatus() != STATUS_WAIT && r.getStatus() != STATUS_HUNG)) {
            return false;
        }
        String stamp = SamDateRule.ts(at);
        int rows = this.zwSamTaskMapper.update(null, new UpdateWrapper<TZwSamTask>()
                .set("status", STATUS_CANCEL)
                .set("cancel_at", at)
                .set("remark", appendTrace(r.getRemark(), "撤单 " + stamp + " " + safe(reason)))
                .set("update_time", at)
                // 只许待派/挂起撤单；due_at/dispatch_at 不在 SET 列里，首次定的采样日与派出时刻永不被覆盖
                .eq("id", id)
                .in("status", STATUS_WAIT, STATUS_HUNG)
                .eq("del_flag", 0));
        return rows > 0;
    }

    @Override
    public boolean transfer(Long id, String reason, Date at) {
        TZwSamTask r = this.zwSamTaskMapper.selectById(id);
        if (r == null || (r.getStatus() != STATUS_WAIT && r.getStatus() != STATUS_HUNG)) {
            return false;
        }
        String stamp = SamDateRule.ts(at);
        int rows = this.zwSamTaskMapper.update(null, new UpdateWrapper<TZwSamTask>()
                .set("status", STATUS_TRANSFER)
                .set("remark", appendTrace(r.getRemark(), "转作他理 " + stamp + " " + safe(reason)))
                .set("update_time", at)
                .eq("id", id)
                .in("status", STATUS_WAIT, STATUS_HUNG)
                .eq("del_flag", 0));
        return rows > 0;
    }

    private static String appendTrace(String old, String line) {
        if (old == null || old.isEmpty()) {
            return line;
        }
        return old + "；" + line;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    /**
     * Quartz 调度入口（invokeTarget：tZwSamTaskServiceImpl.scheduledRun()），无参，时刻取当下。
     */
    public void scheduledRun() {
        SamRunResult result = runOnce(new Date());
        System.out.println("[采样派单] " + result.getOutcome() + " " + result.summary());
    }
}
