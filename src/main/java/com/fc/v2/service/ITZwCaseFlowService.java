package com.fc.v2.service;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.model.auto.TZwCaseFlow;

/**
 * 水质异常处置工单 Service接口（state-machine 形状：单据流转，不提供增删改查全套）
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITZwCaseFlowService {

    /** 按主键查询单据 */
    TZwCaseFlow selectTZwCaseFlowById(Long id);

    /** 列表查询（流转台账） */
    List<TZwCaseFlow> selectTZwCaseFlowList(QueryWrapper<TZwCaseFlow> queryWrapper);

    /** 推进一档：返回更新后的单据；被拒返回 null */
    TZwCaseFlow advance(Long id, String remark);

    /** 回退一档：返回更新后的单据；被拒返回 null */
    TZwCaseFlow rollback(Long id, String remark);

    /** 修改内容：已办结不得再改，被拒返回 false */
    boolean updateContent(Long id, String remark);

    /** 删除单据：已办结不得删除，被拒返回 false */
    boolean remove(Long id);
}
