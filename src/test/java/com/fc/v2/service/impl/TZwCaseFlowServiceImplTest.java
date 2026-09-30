package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.mapper.auto.TZwCaseFlowMapper;
import com.fc.v2.model.auto.TZwCaseFlow;

/**
 * 工单流转：逐档过口、封卷即终态、并发/回退先落地时本次条件更新 0 行按被拒处理。
 */
public class TZwCaseFlowServiceImplTest {

    private TZwCaseFlowMapper mapper;
    private TZwCaseFlowServiceImpl service;

    @BeforeEach
    public void setUp() {
        mapper = Mockito.mock(TZwCaseFlowMapper.class);
        service = new TZwCaseFlowServiceImpl();
        ReflectionTestUtils.setField(service, "zwCaseFlowMapper", mapper);
    }

    private static TZwCaseFlow bill(Integer stage, Integer status) {
        TZwCaseFlow r = new TZwCaseFlow();
        r.setId(1L);
        r.setStage(stage);
        r.setStatus(status);
        r.setDelFlag(0);
        return r;
    }

    @Test
    public void advanceMovesExactlyOneStageOnly() {
        when(mapper.selectById(1L)).thenReturn(bill(1, 1));
        when(mapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        TZwCaseFlow r = service.advance(1L, "初检过口");

        // 一次只准动一档：1 → 2，不是旧实现的一跳两级
        assertEquals(2, r.getStage());
        assertEquals(1, r.getStatus());
    }

    @Test
    public void advanceFromNotStartedAlsoOneStep() {
        when(mapper.selectById(1L)).thenReturn(bill(0, 0));
        when(mapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        TZwCaseFlow r = service.advance(1L, "登记受理");

        assertEquals(1, r.getStage());
        assertEquals(1, r.getStatus());
    }

    @Test
    public void advanceIntoLastStageSealsTheBill() {
        when(mapper.selectById(1L)).thenReturn(bill(2, 1));
        when(mapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        TZwCaseFlow r = service.advance(1L, "复核封卷");

        assertEquals(3, r.getStage());
        assertEquals(2, r.getStatus());
    }

    @Test
    public void advanceRejectedAtLastStageAndAfterSealed() {
        when(mapper.selectById(1L)).thenReturn(bill(3, 2));
        assertNull(service.advance(1L, "还想推进"));
        verify(mapper, never()).update(isNull(), any(Wrapper.class));

        Mockito.reset(mapper);
        when(mapper.selectById(1L)).thenReturn(bill(2, 2));
        assertNull(service.advance(1L, "封存后推进"));
        verify(mapper, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    public void advanceLosesRaceReturnsNull() {
        // 读到 stage=1 在办，落库时已被另一过口（含回退）挪动 → 0 行
        when(mapper.selectById(1L)).thenReturn(bill(1, 1));
        when(mapper.update(isNull(), any(Wrapper.class))).thenReturn(0);

        assertNull(service.advance(1L, "并发的另一记推进"));
    }

    @Test
    public void rollbackStepsBackExactlyOneStageNeverToZero() {
        when(mapper.selectById(1L)).thenReturn(bill(2, 1));
        when(mapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        TZwCaseFlow r = service.rollback(1L, "补材料");

        // 一次只退一档：2 → 1，不是旧实现的一把归 0
        assertEquals(1, r.getStage());
        assertEquals(1, r.getStatus());
    }

    @Test
    public void rollbackRejectedAtFirstStageAndAfterSealed() {
        when(mapper.selectById(1L)).thenReturn(bill(0, 1));
        assertNull(service.rollback(1L, "首道无可退"));
        verify(mapper, never()).update(isNull(), any(Wrapper.class));

        Mockito.reset(mapper);
        when(mapper.selectById(1L)).thenReturn(bill(3, 2));
        assertNull(service.rollback(1L, "封存后回退"));
        verify(mapper, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    public void sealedBillCannotBeEditedOrRemoved() {
        when(mapper.selectById(1L)).thenReturn(bill(3, 2));
        assertFalse(service.updateContent(1L, "改记事"));
        verify(mapper, never()).update(isNull(), any(Wrapper.class));

        // 删除不经先查，条件 DELETE 自带封存闸：封存单命中 0 行 → false
        when(mapper.delete(any(Wrapper.class))).thenReturn(0);
        assertFalse(service.remove(1L));
    }

    @Test
    public void activeBillCanBeEditedAndRemoved() {
        when(mapper.selectById(1L)).thenReturn(bill(1, 1));
        when(mapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        when(mapper.delete(any(Wrapper.class))).thenReturn(1);

        assertTrue(service.updateContent(1L, "补一句记事"));
        assertTrue(service.remove(1L));
    }
}
