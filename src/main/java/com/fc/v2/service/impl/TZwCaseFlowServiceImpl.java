package com.fc.v2.service.impl;

import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.mapper.auto.TZwCaseFlowMapper;
import com.fc.v2.model.auto.TZwCaseFlow;
import com.fc.v2.service.ITZwCaseFlowService;

/**
 * 水质异常处置工单 Service业务层处理（state-machine 形状：单据流转）
 *
 * <p>阶段 0..3 逐档走，每次过口只许动一档：推进 +1（走到 3 复核封卷即封存）、
 * 回退 -1（0 档无可退）。所有过口一律走<b>条件更新</b>——WHERE 必须仍是读到的
 * stage/status，并发另一过口或一次回退先落地，本次就是 0 行，按被拒处理，
 * 两边（推进方/回退方）对同一张单的状态口径永远一致，不许读后写互相覆盖。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TZwCaseFlowServiceImpl implements ITZwCaseFlowService {

    private static final int MIN_STAGE = 0;
    private static final int MAX_STAGE = 3;
    private static final int STATUS_NOT_STARTED = 0;
    private static final int STATUS_ACTIVE = 1;
    private static final int STATUS_TERMINAL = 2;

    @javax.annotation.Resource
    private TZwCaseFlowMapper zwCaseFlowMapper;

    @Override
    public TZwCaseFlow selectTZwCaseFlowById(Long id) {
        return this.zwCaseFlowMapper.selectById(id);
    }

    @Override
    public List<TZwCaseFlow> selectTZwCaseFlowList(QueryWrapper<TZwCaseFlow> queryWrapper) {
        return this.zwCaseFlowMapper.selectList(queryWrapper);
    }

    /**
     * 推进一档：未起/在办单 stage +1；走到复核封卷（3）随口封存（status=2）。
     * 已封存、已在封卷档、查无此单一律拒绝。条件更新兜并发：读到的 stage/status
     * 已被另一过口（含回退）挪动则 0 行，返回 null。
     */
    @Override
    public TZwCaseFlow advance(Long id, String remark) {
        if (id == null) {
            return null;
        }
        TZwCaseFlow r = this.zwCaseFlowMapper.selectById(id);
        if (r == null || r.getStage() == null) {
            return null;
        }
        int fromStatus = r.getStatus() == null ? STATUS_NOT_STARTED : r.getStatus();
        int from = r.getStage();
        if (fromStatus == STATUS_TERMINAL || fromStatus < STATUS_NOT_STARTED || fromStatus > STATUS_ACTIVE
                || from < MIN_STAGE || from >= MAX_STAGE) {
            return null;
        }
        int to = from + 1;
        int toStatus = to >= MAX_STAGE ? STATUS_TERMINAL : STATUS_ACTIVE;

        UpdateWrapper<TZwCaseFlow> uw = new UpdateWrapper<TZwCaseFlow>()
                .set("stage", to)
                .set("status", toStatus)
                .set("last_action", remark)
                .set("update_time", new Date())
                .eq("id", id)
                .eq("stage", from)
                .eq("status", fromStatus)
                .eq("del_flag", 0);
        if (this.zwCaseFlowMapper.update(null, uw) <= 0) {
            return null;
        }
        r.setStage(to);
        r.setStatus(toStatus);
        r.setLastAction(remark);
        return r;
    }

    /**
     * 回退一档：只许在办单 stage -1，一次只退一档，永不一把归到 0 档。
     * 0 档、已封存、查无此单拒绝。条件更新同 {@link #advance}。
     */
    @Override
    public TZwCaseFlow rollback(Long id, String remark) {
        if (id == null) {
            return null;
        }
        TZwCaseFlow r = this.zwCaseFlowMapper.selectById(id);
        if (r == null || r.getStage() == null) {
            return null;
        }
        int from = r.getStage();
        Integer status = r.getStatus();
        if (status == null || status != STATUS_ACTIVE || from <= MIN_STAGE || from > MAX_STAGE) {
            return null;
        }
        int to = from - 1;

        UpdateWrapper<TZwCaseFlow> uw = new UpdateWrapper<TZwCaseFlow>()
                .set("stage", to)
                .set("status", STATUS_ACTIVE)
                .set("last_action", remark)
                .set("update_time", new Date())
                .eq("id", id)
                .eq("stage", from)
                .eq("status", STATUS_ACTIVE)
                .eq("del_flag", 0);
        if (this.zwCaseFlowMapper.update(null, uw) <= 0) {
            return null;
        }
        r.setStage(to);
        r.setStatus(STATUS_ACTIVE);
        r.setLastAction(remark);
        return r;
    }

    /** 修改记事：已封存不得再改 */
    @Override
    public boolean updateContent(Long id, String remark) {
        if (id == null) {
            return false;
        }
        TZwCaseFlow r = this.zwCaseFlowMapper.selectById(id);
        if (r == null) {
            return false;
        }
        if (r.getStatus() != null && r.getStatus() == STATUS_TERMINAL) {
            return false;
        }
        // 条件更新兜底：读到后若已被另一过口封存，0 行不改
        return this.zwCaseFlowMapper.update(null, new UpdateWrapper<TZwCaseFlow>()
                .set("content", remark)
                .set("update_time", new Date())
                .eq("id", id)
                .and(w -> w.ne("status", STATUS_TERMINAL).or().isNull("status"))
                .eq("del_flag", 0)) > 0;
    }

    /** 删除单据：已封存不得删除 */
    @Override
    public boolean remove(Long id) {
        if (id == null) {
            return false;
        }
        return this.zwCaseFlowMapper.delete(new QueryWrapper<TZwCaseFlow>()
                .eq("id", id)
                .and(w -> w.ne("status", STATUS_TERMINAL).or().isNull("status"))
                .eq("del_flag", 0)) > 0;
    }

}
