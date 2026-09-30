package com.fc.v2.service.zw;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.junit.jupiter.api.Test;

/**
 * 全站唯一日期口径的边界用例：把采样日算作已过还是未过，全看这一把尺。
 */
public class SamDateRuleTest {

    private static Date ts(String s) throws Exception {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(s);
    }

    @Test
    public void endOfDayIsFixedAtLastSecond() {
        assertEquals("2026-09-15 23:59:59", SamDateRule.ts(SamDateRule.endOfDay(java.sql.Date.valueOf("2026-09-15"))));
    }

    @Test
    public void leadDaysFloorsFraction() {
        assertEquals(2L, SamDateRule.leadDays(new BigDecimal("2.9")));
        assertEquals(0L, SamDateRule.leadDays(null));
    }

    @Test
    public void surfacedIsInclusiveAtBoundary() throws Exception {
        Date due = ts("2026-09-20 23:59:59");
        // 提前 3 天：2026-09-17 23:59:59 这一刻即浮出（含边界）
        Date edge = ts("2026-09-17 23:59:59");
        Date before = ts("2026-09-17 23:59:58");
        assertTrue(SamDateRule.surfaced(due, new BigDecimal("3"), edge));
        assertFalse(SamDateRule.surfaced(due, new BigDecimal("3"), before));
    }

    @Test
    public void overdueIsExclusiveAtBoundary() throws Exception {
        Date due = ts("2026-09-20 23:59:59");
        // 等于不算过——采样日当天 23:59:59 仍在约定期内，下一秒才算过期
        assertFalse(SamDateRule.overdue(due, due));
        assertTrue(SamDateRule.overdue(due, ts("2026-09-21 00:00:00")));
        assertFalse(SamDateRule.overdue(due, ts("2026-09-20 12:00:00")));
    }

    @Test
    public void urgentOnlyAfterThreeFullDaysPastSurface() throws Exception {
        Date due = ts("2026-09-20 23:59:59");
        // 浮出点 09-17 23:59:59；恰满三天（09-20 23:59:59）不算，超过才算
        assertFalse(SamDateRule.urgent(due, new BigDecimal("3"), ts("2026-09-20 23:59:59")));
        assertTrue(SamDateRule.urgent(due, new BigDecimal("3"), ts("2026-09-21 00:00:00")));
    }
}
