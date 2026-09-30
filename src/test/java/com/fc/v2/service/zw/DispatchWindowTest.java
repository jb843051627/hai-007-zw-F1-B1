package com.fc.v2.service.zw;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;

import org.junit.jupiter.api.Test;

/**
 * 派单窗口：常规两段、汛期两段、法定节假日整日静默、段外原地等下一轮。
 */
public class DispatchWindowTest {

    private static Date ts(String s) throws Exception {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(s);
    }

    private final DispatchWindow window = new DispatchWindow();

    @Test
    public void regularWindowTwoSegmentsWithHalfHourEdges() throws Exception {
        // 含起不含终：08:00 可派，12:00 整点落入午间断档
        assertTrue(window.inWindow(ts("2026-09-16 08:00:00")));
        assertFalse(window.inWindow(ts("2026-09-16 12:00:00")));
        assertTrue(window.inWindow(ts("2026-09-16 11:59:00")));
        // 下午段
        assertTrue(window.inWindow(ts("2026-09-16 14:00:00")));
        assertFalse(window.inWindow(ts("2026-09-16 18:00:00")));
        // 夜间静默
        assertFalse(window.inWindow(ts("2026-09-16 06:00:00")));
        assertFalse(window.inWindow(ts("2026-09-16 22:00:00")));
    }

    @Test
    public void floodSeasonUsesSeparateSegments() throws Exception {
        // 汛期 06-15..06-29，07:00–11:00、13:00–17:00
        assertFalse(window.inWindow(ts("2026-06-15 06:59:00")));
        assertTrue(window.inWindow(ts("2026-06-15 07:00:00")));
        assertTrue(window.inWindow(ts("2026-06-20 10:59:00")));
        assertFalse(window.inWindow(ts("2026-06-20 11:00:00")));
        assertTrue(window.inWindow(ts("2026-06-29 16:59:00")));
        // 06-30 已出汛，按常规窗口：07 点尚不可派
        assertFalse(window.inWindow(ts("2026-06-30 07:30:00")));
        // 06-14 未入汛：09 点按常规窗口可派
        assertTrue(window.inWindow(ts("2026-06-14 09:00:00")));
    }

    @Test
    public void holidaySilencesEntireDayEvenInsideHours() throws Exception {
        window.setHolidays(Collections.singleton("2026-09-16"));
        assertTrue(window.isHoliday(ts("2026-09-16 10:00:00")));
        assertFalse(window.inWindow(ts("2026-09-16 10:00:00")));
        // 节假日次日恢复
        assertTrue(window.inWindow(ts("2026-09-17 10:00:00")));
    }
}
