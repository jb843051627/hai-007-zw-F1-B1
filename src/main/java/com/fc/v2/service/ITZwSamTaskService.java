package com.fc.v2.service;

import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.model.auto.TZwSamTask;
import com.fc.v2.service.zw.SamRunResult;

/**
 * 采样任务到期派单 Service接口（scheduling-job 形状：周期执行，无人工抄录入口）
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITZwSamTaskService {

    /** 按主键回查条目 */
    TZwSamTask selectTZwSamTaskById(Long id);

    /** 台账查询（挂起/撤单条目仍能在页面翻到） */
    List<TZwSamTask> selectTZwSamTaskList(Wrapper<TZwSamTask> queryWrapper);

    /**
     * 该时刻已浮出待办、且尚未过约定终结时刻的待派条目（status=0）。
     * 浮出/过期判定走 {@link com.fc.v2.service.zw.SamDateRule} 唯一口径，
     * 与 {@link #runOnce(Date)} 回写时用的是同一套谓词；窗口是否放行不在此把关。
     */
    List<TZwSamTask> listDue(Date at);

    /**
     * 按检测计划周期幂等生成该浮出的事项条（事项编号/点位代号/约定日期一并从底档带入）。
     * 已存在的（同计划同点位同约定日）不重复落，返回本轮新增条数。
     */
    int generate(Date at);

    /**
     * 跑一轮：窗口外静默 → SILENT；窗口内无应动 → IDLE（"本轮无活"）；
     * 正常处置 → DISPATCHED（三数只统计本轮，且与库内现数出自同一次 GROUP BY 计数）；
     * 轮次本身失败 → ERROR。单条失败跳过继续，不拖垮整轮。
     */
    SamRunResult runOnce(Date at);

    /**
     * 撤单：待派/挂起条目 → 已撤单，钉撤单时刻并在备注留痕。
     * 首次定的采样日 due_at 与已钉的派出时刻 dispatch_at 永不覆盖。
     */
    boolean cancel(Long id, String reason, Date at);

    /**
     * 转作他理：待派/挂起条目 → 转理，备注留痕；同样不碰 due_at/dispatch_at。
     */
    boolean transfer(Long id, String reason, Date at);
}
