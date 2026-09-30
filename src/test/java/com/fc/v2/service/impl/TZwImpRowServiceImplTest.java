package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.mapper.auto.TZwImpRowMapper;
import com.fc.v2.model.auto.TZwImpRow;

/**
 * 整批提交：空批不算错、超限整批不动、坏一行不拖垮全批、重复提交幂等。
 */
public class TZwImpRowServiceImplTest {

    private TZwImpRowMapper mapper;
    private TZwImpRowServiceImpl service;

    @BeforeEach
    public void setUp() {
        mapper = Mockito.mock(TZwImpRowMapper.class);
        service = new TZwImpRowServiceImpl();
        ReflectionTestUtils.setField(service, "zwImpRowMapper", mapper);
    }

    private static TZwImpRow row(String code, String qty) {
        TZwImpRow r = new TZwImpRow();
        r.setItemCode(code);
        r.setQty(qty == null ? null : new BigDecimal(qty));
        return r;
    }

    @Test
    public void emptyBatchIsZeroNotAnError() {
        assertEquals(0, service.submitBatch("B1", new ArrayList<TZwImpRow>()));
        assertEquals(0, service.submitBatch("B1", null));
        verify(mapper, never()).insert(any());
    }

    @Test
    public void oversizedBatchRejectedEntirely() {
        List<TZwImpRow> rows = new ArrayList<TZwImpRow>();
        for (int i = 0; i < 501; i++) {
            rows.add(row("ITEM" + i, "1.0"));
        }
        when(mapper.selectCount(any(Wrapper.class))).thenReturn(0);

        assertEquals(-1, service.submitBatch("B1", rows));
        verify(mapper, never()).insert(any());
    }

    @Test
    public void oneBadRowDoesNotStopTheWholeBatch() {
        TZwImpRow good1 = row("ITEM1", "12.5");
        TZwImpRow bad = row("  ", "0"); // 代号空且读数非正
        TZwImpRow good2 = row("ITEM2", "3.0");
        when(mapper.selectCount(any(Wrapper.class))).thenReturn(0);
        when(mapper.insert(any(TZwImpRow.class))).thenReturn(1);

        int ok = service.submitBatch("B1", new ArrayList<TZwImpRow>(Arrays.asList(good1, bad, good2)));

        // 好行照常入库，坏行只落自己那笔退回明细——整批不许停在坏行上
        assertEquals(2, ok);
        verify(mapper, times(3)).insert(any(TZwImpRow.class));
        assertEquals(1, good1.getStatus().intValue());
        assertEquals(2, bad.getStatus().intValue());
        assertEquals(1, good2.getStatus().intValue());
        // 坏行是原表第 2 行，行号照旧不许重新编号
        assertEquals(2, bad.getRowNo().intValue());
        assertEquals("B1", bad.getBatchNo());
    }

    @Test
    public void missingAndNonPositiveReadingsAreBad() {
        TZwImpRow noReading = row("ITEM1", null);
        TZwImpRow negative = row("ITEM2", "-0.1");
        when(mapper.selectCount(any(Wrapper.class))).thenReturn(0);
        when(mapper.insert(any(TZwImpRow.class))).thenReturn(1);

        int ok = service.submitBatch("B1", new ArrayList<TZwImpRow>(Arrays.asList(noReading, negative)));

        assertEquals(0, ok);
        assertEquals(2, noReading.getStatus().intValue());
        assertEquals(2, negative.getStatus().intValue());
    }

    @Test
    public void duplicateBatchIsIdempotent() {
        // 批次已有 3 笔落地（2 好 1 坏）：直接回成功数，一行都不许再落
        when(mapper.selectCount(any(Wrapper.class))).thenReturn(3, 2);

        int ok = service.submitBatch("B1",
                new ArrayList<TZwImpRow>(Arrays.asList(row("ITEM1", "1.0"), row("ITEM2", "2.0"))));

        assertEquals(2, ok);
        verify(mapper, never()).insert(any());
    }

    @Test
    public void listErrorsDelegatesToMapper() {
        TZwImpRow fail = row("ITEM9", "0");
        fail.setStatus(2);
        when(mapper.selectList(any(Wrapper.class))).thenReturn(Arrays.asList(fail));

        List<TZwImpRow> errors = service.listErrors("B1");

        assertEquals(1, errors.size());
        assertEquals(2, errors.get(0).getStatus().intValue());
    }
}
