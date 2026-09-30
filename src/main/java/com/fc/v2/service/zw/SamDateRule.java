package com.fc.v2.service.zw;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * 采样派单日期口径——全站只能有这一套讲法。
 *
 * <p>翻单（{code listDue}）与回写（{code runOnce}）必须共用此处谓词，禁止各写各的尺子。
 * 约定采样日 {@code due_at} 恒为采样日的<b>终结时刻</b> 23:59:59，只允许从本类构造，
 * 任何业务动作不得挪动；唯一可调档是提前天数 amount（自然日数，小数向下取整）。
 *
 * <p>比较一律按墙钟秒取整后做，不走带时区的 java.util.Date 直接绑定——JDBC serverTimezone
 * 与本机时区不对称时，"恰好到期"这类边界会整体偏移（同域限值线已踩过）。
 *
 * @author fuce
 * @date 2026-09-29
 */
public final class SamDateRule {

    /** 紧急线：浮出后超过这么多个自然日仍没派出去，就算紧急事项 */
    public static final int URGENT_DAYS = 3;

    private static final String TS_PATTERN = "yyyy-MM-dd HH:mm:ss";
    private static final String DAY_PATTERN = "yyyy-MM-dd";

    private SamDateRule() {
    }

    /** 墙钟秒（同一把尺的取整口） */
    public static long epochSecond(Date d) {
        return d.getTime() / 1000L;
    }

    public static String ts(Date d) {
        return new SimpleDateFormat(TS_PATTERN).format(d);
    }

    public static String day(Date d) {
        return new SimpleDateFormat(DAY_PATTERN).format(d);
    }

    /**
     * 约定采样日的起点时刻：当日 00:00:00。
     * 浮出只论自然日——"还剩 amount 天的那个自然日"一开盘，事项就该在待办名下，
     * 不许锚在 23:59:59（当天末轮窗口 18:00 已关，正好到点的条目会被整体推到下一轮）。
     */
    public static Date startOfDay(Date day) {
        Calendar c = Calendar.getInstance();
        c.setTime(day);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTime();
    }

    /**
     * 约定采样日的终结时刻：当日 23:59:59。
     * due_at 的唯一构造口——生成事项条时走这里，别处不许 new 一个别的钟点。
     */
    public static Date endOfDay(Date day) {
        Calendar c = Calendar.getInstance();
        c.setTime(day);
        c.set(Calendar.HOUR_OF_DAY, 23);
        c.set(Calendar.MINUTE, 59);
        c.set(Calendar.SECOND, 59);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTime();
    }

    /** 提前天数取整：自然日数，小数向下取整（2.9 天按 2 天算） */
    public static long leadDays(BigDecimal amount) {
        if (amount == null) {
            return 0L;
        }
        return amount.setScale(0, RoundingMode.FLOOR).longValue();
    }

    /**
     * 浮出起始时刻 =（约定日往前推 amount 个自然日）当天的 00:00:00。
     * "还剩 amount 天的那个自然日"一开盘事项就浮出，保证当天首个值班窗口（08:00）即可派出；
     * 锚 23:59:59 会让当天两轮窗口全部错过，事项整体延后一轮才处理。
     */
    public static Date surfaceAt(Date dueAt, BigDecimal amount) {
        Calendar c = Calendar.getInstance();
        c.setTime(dueAt);
        c.add(Calendar.DAY_OF_MONTH, (int) -leadDays(amount));
        return startOfDay(c.getTime());
    }

    /**
     * 是否已浮出待办：{@code at >= 浮出自然日 00:00:00}（含边界——自然日开盘即浮出）。
     */
    public static boolean surfaced(Date dueAt, BigDecimal amount, Date at) {
        return epochSecond(at) >= epochSecond(surfaceAt(dueAt, amount));
    }

    /**
     * 是否已过约定时刻：{@code at > dueAt}。
     * <b>等于不算过</b>——采样日当天 23:59:59 仍在约定期内，下一秒才算过期。
     */
    public static boolean overdue(Date dueAt, Date at) {
        return epochSecond(at) > epochSecond(dueAt);
    }

    /**
     * 是否紧急：浮出待办后超过 {@link #URGENT_DAYS} 天仍未派出。
     * 即 {@code at > 浮出起始 + 3天}（恰满 3 天的那一秒不算，超过才算）。
     */
    public static boolean urgent(Date dueAt, BigDecimal amount, Date at) {
        Date deadline = surfaceAt(dueAt, amount);
        Calendar c = Calendar.getInstance();
        c.setTime(deadline);
        c.add(Calendar.DAY_OF_MONTH, URGENT_DAYS);
        return epochSecond(at) > epochSecond(c.getTime());
    }
}
