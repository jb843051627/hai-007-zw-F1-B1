package com.fc.v2.service.impl;

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
 * <p>每次过口只准走一档：推进 +1、回退 -1，不允许跳档/一把清零。
 * 推进到复核封卷档（{@link #MAX_STAGE}）必须同步把工单落定为"已封存"，
 * 封了卷的单不再参与后续执行。所有流转一律走带"当前档/落定情形"条件的原子更新，
 * 不靠读后改——并发推进与回退撞在一起时，谁先落地谁算数，后到的一条 0 行被拒，
 * 两边看到的档位与落定情形始终同一口径。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TZwCaseFlowServiceImpl implements ITZwCaseFlowService {

    private static final int MIN_STAGE = 0;
    private static final int MAX_STAGE = 3;
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
     * 推进一档：登记→初检→处置→复核封卷。到顶（已封存）再推被拒；
     * 与回退并发时以库内当前档为准，条件不满足（0 行）即被拒。
     */
    @Override
    public TZwCaseFlow advance(Long id, String remark) {
        TZwCaseFlow r = this.zwCaseFlowMapper.selectById(id);
        if (r == null) {
            return null;
        }
        int st = r.getStage() == null ? MIN_STAGE : r.getStage();
        if (st >= MAX_STAGE) {
            // 已封卷，不许再推进、不许再参与一轮执行
            return null;
        }
        int next = st + 1;
        int rows = this.zwCaseFlowMapper.update(null, new UpdateWrapper<TZwCaseFlow>()
                .set("stage", next)
                // 推进到顶档与"已封存"同一条更新钉死，缺一样都不算成
                .set("status", next >= MAX_STAGE ? STATUS_TERMINAL : STATUS_ACTIVE)
                .set("last_action", remark)
                .eq("id", id)
                .eq("stage", st)
                .ne("status", STATUS_TERMINAL)
                .eq("del_flag", 0));
        return rows > 0 ? this.zwCaseFlowMapper.selectById(id) : null;
    }

    /**
     * 回退一档：只退一格，退回后一律"在办"；登记档（0 档）无可退，被拒。
     * 与推进并发时同样靠条件更新兜底，0 行即被拒。
     */
    @Override
    public TZwCaseFlow rollback(Long id, String remark) {
        TZwCaseFlow r = this.zwCaseFlowMapper.selectById(id);
        if (r == null) {
            return null;
        }
        int st = r.getStage() == null ? MIN_STAGE : r.getStage();
        if (st <= MIN_STAGE) {
            return null;
        }
        int prev = st - 1;
        int rows = this.zwCaseFlowMapper.update(null, new UpdateWrapper<TZwCaseFlow>()
                .set("stage", prev)
                // 档位回退与落定情形在同一条更新里改回"在办"，不许留着"已封存/已核讫"的旧口径
                .set("status", STATUS_ACTIVE)
                .set("last_action", remark)
                .eq("id", id)
                .eq("stage", st)
                .eq("del_flag", 0));
        return rows > 0 ? this.zwCaseFlowMapper.selectById(id) : null;
    }

    @Override
    public boolean updateContent(Long id, String remark) {
        TZwCaseFlow r = this.zwCaseFlowMapper.selectById(id);
        if (r == null) {
            return false;
        }
        // 已封卷的单内容封死，不许再改
        if (r.getStatus() != null && r.getStatus() == STATUS_TERMINAL) {
            return false;
        }
        int rows = this.zwCaseFlowMapper.update(null, new UpdateWrapper<TZwCaseFlow>()
                .set("content", remark)
                .eq("id", id)
                .ne("status", STATUS_TERMINAL)
                .eq("del_flag", 0));
        return rows > 0;
    }

    @Override
    public boolean remove(Long id) {
        TZwCaseFlow r = this.zwCaseFlowMapper.selectById(id);
        if (r == null) {
            return false;
        }
        // 已封卷的单留档，不得删除
        if (r.getStatus() != null && r.getStatus() == STATUS_TERMINAL) {
            return false;
        }
        return this.zwCaseFlowMapper.deleteById(id) > 0;
    }

}
