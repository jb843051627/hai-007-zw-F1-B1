package com.fc.v2.service.zw;

import java.util.ArrayList;
import java.util.List;

/**
 * 一轮派单的结果。
 *
 * <p>三种"没干活"必须分得清，不许混成一句"执行出错"：
 * <ul>
 *   <li>{@link Outcome#SILENT}：窗口之外（夜间/法定节假日/汛期段外），整段静默，原地等下一轮；</li>
 *   <li>{@link Outcome#IDLE}：窗口内但确实没有应动事项——"本轮无活"；</li>
 *   <li>{@link Outcome#ERROR}：轮次本身执行出错（候选条目捞不出来等）。</li>
 * </ul>
 *
 * <p>{@link Outcome#DISPATCHED} 下三数只统计本轮候选条目，且与库内现数出自轮末同一次 GROUP BY 计数。
 *
 * @author fuce
 * @date 2026-09-29
 */
public class SamRunResult {

    public enum Outcome {
        /** 本轮有动作（含全部挂起、零派出的情形——只要条目被处置过就不是"无活"） */
        DISPATCHED,
        /** 窗口内无应动事项 */
        IDLE,
        /** 窗口之外，整段静默 */
        SILENT,
        /** 执行出错 */
        ERROR
    }

    private final Outcome outcome;
    private final String message;
    private final int generated;
    private final int waiting;
    private final int dispatched;
    private final int hung;
    private final List<String> itemErrors;

    public SamRunResult(Outcome outcome, String message, int generated,
                        int waiting, int dispatched, int hung, List<String> itemErrors) {
        this.outcome = outcome;
        this.message = message;
        this.generated = generated;
        this.waiting = waiting;
        this.dispatched = dispatched;
        this.hung = hung;
        this.itemErrors = itemErrors == null ? new ArrayList<String>() : itemErrors;
    }

    public static SamRunResult silent(String message) {
        return new SamRunResult(Outcome.SILENT, message, 0, 0, 0, 0, null);
    }

    public static SamRunResult idle() {
        return new SamRunResult(Outcome.IDLE, "本轮无活", 0, 0, 0, 0, null);
    }

    public static SamRunResult error(String message) {
        return new SamRunResult(Outcome.ERROR, message, 0, 0, 0, 0, null);
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public String getMessage() {
        return message;
    }

    public int getGenerated() {
        return generated;
    }

    public int getWaiting() {
        return waiting;
    }

    public int getDispatched() {
        return dispatched;
    }

    public int getHung() {
        return hung;
    }

    public List<String> getItemErrors() {
        return itemErrors;
    }

    /** 屏上亮的总结句（只统计本轮） */
    public String summary() {
        return "本轮：生成 " + generated + " 条，待派 " + waiting + " 条，已派 "
                + dispatched + " 条，挂起 " + hung + " 条";
    }
}
