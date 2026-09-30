package com.fc.v2.service.zw;

import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * 派单值班窗口。
 *
 * <p>常规值班每天分两段：08:00–12:00、14:00–18:00；两段之外（含午间断档与夜间）整段静默，
 * 即使有到期事项也原地等下一轮。法定节假日整日静默，优先级高于一切窗口。
 *
 * <p>汛期半个月（06-15 起 15 天，06-15..06-29）单独排两段：07:00–11:00、13:00–17:00，
 * 不走常规窗口。窗口只决定"这一轮动不动"；同一事项当日二次进来的去重，由派发时的
 * 条件更新（status 必须仍是待派）兜住，先前已落地的那条原样保留。
 *
 * <p>边界一律<b>含起不含终</b>。法定节假日清单默认空，由调度配置注入（{@link #setHolidays}）。
 *
 * @author fuce
 * @date 2026-09-29
 */
@Component
public class DispatchWindow {

    /** 常规值班两段（24 小时制，含起不含终） */
    static final int REG_1_FROM = 8 * 60;
    static final int REG_1_TO = 12 * 60;
    static final int REG_2_FROM = 14 * 60;
    static final int REG_2_TO = 18 * 60;

    /** 汛期两段（不走常规窗口） */
    static final int FLOOD_1_FROM = 7 * 60;
    static final int FLOOD_1_TO = 11 * 60;
    static final int FLOOD_2_FROM = 13 * 60;
    static final int FLOOD_2_TO = 17 * 60;

    /** 汛期：6 月 15 日起 15 个自然日（含首尾） */
    static final int FLOOD_MONTH = Calendar.JUNE;
    static final int FLOOD_START_DAY = 15;
    static final int FLOOD_HALF_MONTH_DAYS = 15;

    private volatile Set<String> holidays = Collections.emptySet();

    /** 注入法定节假日清单（yyyy-MM-dd），由调度配置处维护 */
    public void setHolidays(Set<String> holidayDays) {
        this.holidays = holidayDays == null
                ? Collections.<String>emptySet()
                : Collections.unmodifiableSet(new HashSet<String>(holidayDays));
    }

    /** 法定节假日整日静默 */
    public boolean isHoliday(Date at) {
        return holidays.contains(SamDateRule.day(at));
    }

    /** 是否落在汛期半个月内 */
    public boolean isFloodSeason(Date at) {
        Calendar c = Calendar.getInstance();
        c.setTime(at);
        if (c.get(Calendar.MONTH) != FLOOD_MONTH) {
            return false;
        }
        int day = c.get(Calendar.DAY_OF_MONTH);
        return day >= FLOOD_START_DAY && day < FLOOD_START_DAY + FLOOD_HALF_MONTH_DAYS;
    }

    /**
     * 此刻是否可派单。法定节假日 → 否；汛期 → 汛期两段；其余 → 常规两段。
     */
    public boolean inWindow(Date at) {
        if (at == null || isHoliday(at)) {
            return false;
        }
        Calendar c = Calendar.getInstance();
        c.setTime(at);
        int minute = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
        if (isFloodSeason(at)) {
            return between(minute, FLOOD_1_FROM, FLOOD_1_TO)
                    || between(minute, FLOOD_2_FROM, FLOOD_2_TO);
        }
        return between(minute, REG_1_FROM, REG_1_TO)
                || between(minute, REG_2_FROM, REG_2_TO);
    }

    /** 含起不含终 */
    private static boolean between(int minute, int from, int to) {
        return minute >= from && minute < to;
    }
}
