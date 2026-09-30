package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import com.fc.v2.mapper.auto.TZwStopBillMapper;
import com.fc.v2.model.auto.TZwStopBill;

/**
 * 签核链：聚印点齐才转道、末道齐印核讫、否决落打回、退道清零、终态一律锁。
 */
public class TZwStopBillServiceImplTest {

    private TZwStopBillMapper mapper;
    private TZwStopBillServiceImpl service;

    @BeforeEach
    public void setUp() {
        mapper = Mockito.mock(TZwStopBillMapper.class);
        service = new TZwStopBillServiceImpl();
        ReflectionTestUtils.setField(service, "zwStopBillMapper", mapper);
    }

    private static TZwStopBill bill(Integer node, Integer signMode, Integer need, Integer signed, Integer status) {
        TZwStopBill r = new TZwStopBill();
        r.setId(1L);
        r.setNodeNo(node);
        r.setSignMode(signMode);
        r.setNeedCount(need);
        r.setSignCount(signed);
        r.setStatus(status);
        r.setDelFlag(0);
        return r;
    }

    @Test
    public void orModeOneSignatureAdvancesNode() {
        // 业务提道，任一人签法，首印即齐 → 转水质核道，印数清零
        when(mapper.selectById(1L)).thenReturn(bill(0, 0, null, 0, 0));
        when(mapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        TZwStopBill r = service.approve(1L, "张三", "同意");

        assertEquals(1, r.getNodeNo());
        assertEquals(0, r.getSignCount());
        assertEquals(0, r.getStatus());
    }

    @Test
    public void andModeCountsEachSignatureUntilQuorum() {
        // 两名点齐：第一印留在本口
        when(mapper.selectById(1L)).thenReturn(bill(0, 1, null, 0, 0));
        when(mapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        TZwStopBill first = service.approve(1L, "张三", "同意");
        assertEquals(0, first.getNodeNo());
        assertEquals(1, first.getSignCount());

        // 第二印点齐 → 转下一道，清零
        Mockito.reset(mapper);
        when(mapper.selectById(1L)).thenReturn(bill(0, 1, null, 1, 0));
        when(mapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        TZwStopBill second = service.approve(1L, "李四", "同意");
        assertEquals(1, second.getNodeNo());
        assertEquals(0, second.getSignCount());
    }

    @Test
    public void lastNodeQuorumPasses() {
        // 调度准（末道），应到 2 印，这是第二印 → 核讫，不转道
        when(mapper.selectById(1L)).thenReturn(bill(2, 1, 2, 1, 0));
        when(mapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        TZwStopBill r = service.approve(1L, "王五", "准予");

        assertEquals(2, r.getNodeNo());
        assertEquals(2, r.getSignCount());
        assertEquals(1, r.getStatus());
    }

    @Test
    public void approveRejectedWhenNodeAlreadyFullySigned() {
        when(mapper.selectById(1L)).thenReturn(bill(0, 0, 1, 1, 0));
        assertNull(service.approve(1L, "多来的一票", "同意"));
        verify(mapper, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    public void approveRejectedAfterPassedOrVetoed() {
        when(mapper.selectById(1L)).thenReturn(bill(2, 0, 1, 1, 1));
        assertNull(service.approve(1L, "张三", "核讫后再签"));
        Mockito.reset(mapper);
        when(mapper.selectById(1L)).thenReturn(bill(1, 0, 1, 0, 2));
        assertNull(service.approve(1L, "张三", "打回后再签"));
        verify(mapper, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    public void approveWithoutApproverRejected() {
        assertNull(service.approve(1L, "  ", "同意"));
        verify(mapper, never()).selectById(any());
    }

    @Test
    public void rejectActuallyVetoes() {
        // 旧实现 reject 只 updateById 原样写回，状态没动——这里必须落成"已打回"
        when(mapper.selectById(1L)).thenReturn(bill(1, 0, 1, 0, 0));
        when(mapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        TZwStopBill r = service.reject(1L, "赵六", "不合规");

        assertEquals(2, r.getStatus());
    }

    @Test
    public void rejectRejectedWhenNotRunning() {
        when(mapper.selectById(1L)).thenReturn(bill(2, 0, 1, 1, 1));
        assertNull(service.reject(1L, "赵六", "核讫后否决"));
        verify(mapper, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    public void rollbackReturnsOneNodeAndClearsSignatures() {
        // 在水质核道已落 1 印，退回业务提道：道次 -1，本口印数作废
        when(mapper.selectById(1L)).thenReturn(bill(1, 1, 2, 1, 0));
        when(mapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        TZwStopBill r = service.rollback(1L, "材料不齐退回");

        assertEquals(0, r.getNodeNo());
        assertEquals(0, r.getSignCount());
        assertEquals(0, r.getStatus());
    }

    @Test
    public void rollbackRejectedAtFirstNodeAndAfterTerminal() {
        when(mapper.selectById(1L)).thenReturn(bill(0, 0, null, 0, 0));
        assertNull(service.rollback(1L, "首道无可退"));
        verify(mapper, never()).update(isNull(), any(Wrapper.class));

        Mockito.reset(mapper);
        when(mapper.selectById(1L)).thenReturn(bill(2, 0, 1, 1, 1));
        assertNull(service.rollback(1L, "核讫后退回"));
        verify(mapper, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    public void lostConcurrentUpdateReturnsNull() {
        when(mapper.selectById(1L)).thenReturn(bill(0, 0, null, 0, 0));
        when(mapper.update(isNull(), any(Wrapper.class))).thenReturn(0);
        assertNull(service.approve(1L, "张三", "并发那一记"));
    }
}
