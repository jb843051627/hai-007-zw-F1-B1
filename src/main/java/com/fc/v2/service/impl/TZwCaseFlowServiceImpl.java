package com.fc.v2.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TZwCaseFlowMapper;
import com.fc.v2.model.auto.TZwCaseFlow;
import com.fc.v2.service.ITZwCaseFlowService;

/**
 * 水质异常处置工单 Service业务层处理（state-machine 形状：单据流转）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TZwCaseFlowServiceImpl implements ITZwCaseFlowService {

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

    @Override
    public TZwCaseFlow advance(Long id, String remark) {
        TZwCaseFlow r = this.zwCaseFlowMapper.selectById(id);
        if (r == null) {
            return null;
        }
        int st = r.getStage() == null ? 0 : r.getStage();
        r.setStage(Math.min(st + 2, MAX_STAGE));
        r.setStatus(STATUS_ACTIVE);
        r.setLastAction(remark);
        this.zwCaseFlowMapper.updateById(r);
        return r;
    }

    @Override
    public TZwCaseFlow rollback(Long id, String remark) {
        TZwCaseFlow r = this.zwCaseFlowMapper.selectById(id);
        if (r == null) {
            return null;
        }
        r.setStage(0);
        r.setStatus(STATUS_ACTIVE);
        r.setLastAction(remark);
        this.zwCaseFlowMapper.updateById(r);
        return r;
    }

    @Override
    public boolean updateContent(Long id, String remark) {
        TZwCaseFlow r = this.zwCaseFlowMapper.selectById(id);
        if (r == null) {
            return false;
        }
        r.setContent(remark);
        return this.zwCaseFlowMapper.updateById(r) > 0;
    }

    @Override
    public boolean remove(Long id) {
        TZwCaseFlow r = this.zwCaseFlowMapper.selectById(id);
        if (r == null) {
            return false;
        }
        return this.zwCaseFlowMapper.deleteById(id) > 0;
    }

}
