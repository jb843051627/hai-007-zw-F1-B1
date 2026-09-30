package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.mapper.auto.TZwArchCardMapper;
import com.fc.v2.mapper.auto.TZwSourceMapper;
import com.fc.v2.model.auto.TZwArchCard;
import com.fc.v2.model.auto.TZwSource;

/**
 * 档案卡：列表/详情同一未删口径（不暗滤进展、不抢分页）；新增与编辑同一套
 * 底档校验与分档尺（等于上限算高一档）。
 */
public class TZwArchCardServiceImplTest {

    private TZwArchCardMapper cardMapper;
    private TZwSourceMapper sourceMapper;
    private TZwArchCardServiceImpl service;

    @BeforeEach
    public void setUp() {
        cardMapper = Mockito.mock(TZwArchCardMapper.class);
        sourceMapper = Mockito.mock(TZwSourceMapper.class);
        service = new TZwArchCardServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", cardMapper);
        ReflectionTestUtils.setField(service, "zwSourceMapper", sourceMapper);
    }

    private static TZwSource source(long id, int status) {
        TZwSource s = new TZwSource();
        s.setId(id);
        s.setSiteNo("ZS00");
        s.setStatus(status);
        s.setDelFlag(0);
        s.setTh1Max(new BigDecimal("30"));
        s.setTh2Max(new BigDecimal("80"));
        s.setTh3Max(new BigDecimal("200"));
        return s;
    }

    private static TZwArchCard card(String billNo, Integer siteId, String qty) {
        TZwArchCard c = new TZwArchCard();
        c.setBillNo(billNo);
        c.setSiteId(siteId);
        c.setQty(qty == null ? null : new BigDecimal(qty));
        return c;
    }

    @Test
    public void listKeepsCallerConditionsAndOnlyAddsDeletedFilter() {
        QueryWrapper<TZwArchCard> qw = new QueryWrapper<TZwArchCard>().eq("status", 1);
        service.selectTZwArchCardList(qw);
        // 调用方条件没被像旧实现那样丢掉重建，且统一补了未删口径；不再硬编码 status=0
        String sql = qw.getSqlSegment();
        assertTrue(sql.contains("status"), sql);
        assertTrue(sql.contains("del_flag"), sql);
        verify(cardMapper).selectList(qw);
    }

    @Test
    public void insertRejectsMissingOrRetiredSource() {
        // 底档查无
        when(sourceMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        assertEquals(0, service.insertTZwArchCard(card("B1", 9, "10")));
        verify(cardMapper, never()).insert(any());

        // 底档已退役
        Mockito.reset(sourceMapper, cardMapper);
        when(sourceMapper.selectOne(any(Wrapper.class))).thenReturn(source(1, 1));
        assertEquals(0, service.insertTZwArchCard(card("B1", 1, "10")));
        verify(cardMapper, never()).insert(any());
    }

    @Test
    public void insertRejectsDuplicateBillNo() {
        when(sourceMapper.selectOne(any(Wrapper.class))).thenReturn(source(0, 0));
        when(cardMapper.selectCount(any(Wrapper.class))).thenReturn(1);

        assertEquals(0, service.insertTZwArchCard(card("DUP", 0, "10")));
        verify(cardMapper, never()).insert(any());
    }

    @Test
    public void insertBandsEqualUpperBoundOneTierHigher() {
        when(sourceMapper.selectOne(any(Wrapper.class))).thenReturn(source(0, 0));
        when(cardMapper.selectCount(any(Wrapper.class))).thenReturn(0);
        when(cardMapper.insert(any(TZwArchCard.class))).thenReturn(1);

        TZwArchCard atEdge = card("B1", 0, "30");   // 等于一档上限 → 二档
        service.insertTZwArchCard(atEdge);
        assertEquals(2, atEdge.getGradeLevel().intValue());

        TZwArchCard below = card("B2", 0, "29.99"); // 严格小于 → 一档
        service.insertTZwArchCard(below);
        assertEquals(1, below.getGradeLevel().intValue());

        TZwArchCard over = card("B3", 0, "200.01"); // 超三档上限 → 四档
        service.insertTZwArchCard(over);
        assertEquals(4, over.getGradeLevel().intValue());
    }

    @Test
    public void editRecomputesBandWithSameRulerAndRejectsRetiredSource() {
        TZwArchCard old = card("B1", 0, "10");
        old.setId(1L);
        old.setGradeLevel(1);
        when(cardMapper.selectOne(any(Wrapper.class))).thenReturn(old);
        when(sourceMapper.selectOne(any(Wrapper.class))).thenReturn(source(0, 0));
        when(cardMapper.update(any(), any(Wrapper.class))).thenReturn(1);

        TZwArchCard edit = new TZwArchCard();
        edit.setId(1L);
        edit.setBillNo("B1");
        edit.setQty(new BigDecimal("80")); // 等于二档上限 → 三档（旧实现编辑从不重算）
        int rows = service.updateTZwArchCard(edit);

        assertEquals(1, rows);
        assertEquals(3, edit.getGradeLevel().intValue());
        assertEquals("ZS00", edit.getSiteNo());
    }

    @Test
    public void editRejectedWhenCardMissingOrSourceRetired() {
        // 卡不存在
        TZwArchCard missing = card("B9", 0, "10");
        missing.setId(3L);
        when(cardMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        assertEquals(0, service.updateTZwArchCard(missing));

        // 卡在，但底档已退役
        TZwArchCard old = card("B1", 0, "10");
        old.setId(2L);
        Mockito.reset(cardMapper);
        when(cardMapper.selectOne(any(Wrapper.class))).thenReturn(old);
        when(sourceMapper.selectOne(any(Wrapper.class))).thenReturn(source(1, 1));
        TZwArchCard edit = card("B1", 1, "10");
        edit.setId(2L);
        assertEquals(0, service.updateTZwArchCard(edit));
        verify(cardMapper, never()).update(any(), any(Wrapper.class));
    }
}
