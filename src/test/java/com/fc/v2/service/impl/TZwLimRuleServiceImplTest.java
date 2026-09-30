package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.mapper.auto.TZwLimRuleMapper;
import com.fc.v2.model.auto.TZwLimRule;

/**
 * 限值线：生效区间 [起, 交) 一把尺（定位/列表/单条/角标同口径）、等于上限取高一档、越界判 0。
 */
public class TZwLimRuleServiceImplTest {

    private TZwLimRuleMapper mapper;
    private TZwLimRuleServiceImpl service;

    @BeforeEach
    public void setUp() {
        mapper = Mockito.mock(TZwLimRuleMapper.class);
        service = new TZwLimRuleServiceImpl();
        ReflectionTestUtils.setField(service, "zwLimRuleMapper", mapper);
    }

    private static Date ts(String s) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static TZwLimRule rule(String code, String start, String end, Integer priority, Integer status) {
        TZwLimRule r = new TZwLimRule();
        r.setId(1L);
        r.setRuleCode(code);
        r.setEffStart(start == null ? null : ts(start));
        r.setEffEnd(end == null ? null : ts(end));
        r.setPriority(priority);
        r.setStatus(status);
        r.setDelFlag(0);
        r.setTh1Max(new BigDecimal("10"));
        r.setTh2Max(new BigDecimal("20"));
        r.setTh3Max(new BigDecimal("30"));
        return r;
    }

    @Test
    public void evaluateUsesEffectiveWindowHalfOpen() {
        // 生效 [09-01, 10-01)
        when(mapper.selectList(any(Wrapper.class)))
                .thenReturn(Arrays.asList(rule("R1", "2026-09-01 00:00:00", "2026-10-01 00:00:00", 5, 0)));

        assertEquals(1, service.evaluate("R1", new BigDecimal("9.99"), ts("2026-09-15 12:00:00")));
        // 恰在启用之日：生效
        assertEquals(1, service.evaluate("R1", new BigDecimal("5"), ts("2026-09-01 00:00:00")));
        // 恰在交棒之日：已不含 → 无生效版本 → 0
        assertEquals(0, service.evaluate("R1", new BigDecimal("5"), ts("2026-10-01 00:00:00")));
    }

    @Test
    public void equalToUpperBoundGoesOneTierHigher() {
        when(mapper.selectList(any(Wrapper.class)))
                .thenReturn(Arrays.asList(rule("R1", null, null, 5, 0)));

        // 与底档种子同口径：等于上限算高一档
        assertEquals(2, service.evaluate("R1", new BigDecimal("10"), ts("2026-09-15 12:00:00")));
        assertEquals(3, service.evaluate("R1", new BigDecimal("20"), ts("2026-09-15 12:00:00")));
        assertEquals(4, service.evaluate("R1", new BigDecimal("30"), ts("2026-09-15 12:00:00")));
        assertEquals(4, service.evaluate("R1", new BigDecimal("30.01"), ts("2026-09-15 12:00:00")));
        assertEquals(1, service.evaluate("R1", new BigDecimal("9.99"), ts("2026-09-15 12:00:00")));
    }

    @Test
    public void outOfRangeAndMissingArgsReturnZero() {
        assertEquals(0, service.evaluate("R1", new BigDecimal("-0.01"), ts("2026-09-15 12:00:00")));
        assertEquals(0, service.evaluate("R1", new BigDecimal("100.01"), ts("2026-09-15 12:00:00")));
        assertEquals(0, service.evaluate("  ", new BigDecimal("5"), ts("2026-09-15 12:00:00")));
        assertEquals(0, service.evaluate("R1", null, ts("2026-09-15 12:00:00")));
    }

    @Test
    public void evaluateTopPicksHighestPriorityEffectiveRule() {
        TZwLimRule low = rule("LOW", null, null, 1, 0);
        TZwLimRule high = rule("HIGH", null, null, 9, 0);
        high.setTh1Max(new BigDecimal("1"));
        when(mapper.selectList(any(Wrapper.class))).thenReturn(Arrays.asList(low, high));

        // 优先级 9 的 HIGH 先说：0.5 < 1 落 1 档
        assertEquals(1, service.evaluateTop(new BigDecimal("0.5"), ts("2026-09-15 12:00:00")));
    }

    @Test
    public void usableSharesTheSameEffectiveRule() {
        TZwLimRule active = rule("R1", null, null, 1, 0);
        TZwLimRule retired = rule("R2", null, null, 1, 1);
        TZwLimRule notYet = rule("R3", "2026-10-01 00:00:00", null, 1, 0);
        TZwLimRule handedOver = rule("R4", null, "2026-09-01 00:00:00", 1, 0);

        when(mapper.selectById(1L)).thenReturn(active);
        when(mapper.selectById(2L)).thenReturn(retired);
        when(mapper.selectById(3L)).thenReturn(notYet);
        when(mapper.selectById(4L)).thenReturn(handedOver);

        Date at = ts("2026-09-15 12:00:00");
        assertTrue(service.usable(1L, at));
        assertFalse(service.usable(2L, at));
        assertFalse(service.usable(3L, at));
        assertFalse(service.usable(4L, at));
        assertFalse(service.usable(null, at));
    }

    @Test
    public void listAvailableSortsByPriorityDescAndCountUsesSameBase() {
        TZwLimRule a = rule("A", null, null, 1, 0);
        TZwLimRule b = rule("B", null, null, 9, 0);
        List<TZwLimRule> dbRows = new ArrayList<TZwLimRule>(Arrays.asList(a, b));
        when(mapper.selectList(any(Wrapper.class))).thenReturn(dbRows);
        when(mapper.selectCount(any(Wrapper.class))).thenReturn(2);

        List<TZwLimRule> avail = service.listAvailable(ts("2026-09-15 12:00:00"));
        assertEquals("B", avail.get(0).getRuleCode());
        assertEquals(2, service.countAvailable(ts("2026-09-15 12:00:00")));
    }

    @Test
    public void findRulePicksLatestEffectiveVersion() {
        // 同代号两版：旧版 [01-01,09-01) 已交棒、新版 [09-01,null) 现行——只应拿到新版
        TZwLimRule oldV = rule("R1", "2026-01-01 00:00:00", "2026-09-01 00:00:00", 1, 0);
        oldV.setId(10L);
        TZwLimRule newV = rule("R1", "2026-09-01 00:00:00", null, 1, 0);
        newV.setId(11L);
        when(mapper.selectList(any(Wrapper.class))).thenReturn(Arrays.asList(oldV, newV));

        assertEquals(1, service.evaluate("R1", new BigDecimal("5"), ts("2026-09-15 12:00:00")));
    }
}
