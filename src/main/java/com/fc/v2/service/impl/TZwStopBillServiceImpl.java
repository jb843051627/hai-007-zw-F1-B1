package com.fc.v2.service.impl;

import java.util.Date;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.mapper.auto.TZwStopBillMapper;
import com.fc.v2.model.auto.TZwStopBill;
import com.fc.v2.service.ITZwStopBillService;

/**
 * 停供/限供申请签核单 Service业务层处理（approval-chain 形状：多阶段签批）
 *
 * <p>三道（0 业务提 / 1 水质核 / 2 调度准）逐道过口，同口并印办法由 sign_mode 定：
 * 0 任一人（应到 1 印）、1 两名点齐（应到 2 印），need_count 有维护时以它为准。
 * 每落一印 sign_count +1，点齐本口才许转下一道并把印数清零；末道点齐即核讫。
 *
 * <p>落印、否决、退道全走<b>条件更新</b>——WHERE 必须仍是读到的 node_no/sign_count/status，
 * 并发两记印或退道先落地时本次 0 行按被拒处理，不许多记一印、不许两边状态对不上。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TZwStopBillServiceImpl implements ITZwStopBillService {

    private static final int MIN_NODE = 0;
    private static final int MAX_NODE = 2;
    private static final int MODE_OR = 0;
    private static final int MODE_AND = 1;
    private static final int AND_NEED = 2;
    private static final int OR_NEED = 1;
    private static final int STATUS_RUNNING = 0;
    private static final int STATUS_PASS = 1;
    private static final int STATUS_VETO = 2;

    @javax.annotation.Resource
    private TZwStopBillMapper zwStopBillMapper;

    @Override
    public TZwStopBill selectTZwStopBillById(Long id) {
        return this.zwStopBillMapper.selectById(id);
    }

    /**
     * 签批一票：本口已落印 +1；点齐应到印数才转下一道（印数清零），末道点齐即核讫。
     * 非在核、本口已点齐、查无此单一律拒绝；条件更新兜并发与重复提交。
     */
    @Override
    public TZwStopBill approve(Long id, String approver, String comment) {
        if (id == null || approver == null || approver.trim().isEmpty()) {
            return null;
        }
        TZwStopBill r = this.zwStopBillMapper.selectById(id);
        if (r == null || r.getNodeNo() == null || r.getStatus() == null
                || r.getStatus() != STATUS_RUNNING) {
            return null;
        }
        int node = r.getNodeNo();
        if (node < MIN_NODE || node > MAX_NODE) {
            return null;
        }
        int signed = r.getSignCount() == null ? 0 : r.getSignCount();
        int need = needOf(r);
        if (signed >= need) {
            // 本口已点齐，多来的这一票不许再记
            return null;
        }
        int nowSigned = signed + 1;

        UpdateWrapper<TZwStopBill> uw = new UpdateWrapper<TZwStopBill>()
                .eq("id", id)
                .eq("node_no", node)
                .eq("status", STATUS_RUNNING)
                .eq("del_flag", 0);
        if (nowSigned >= need) {
            if (node >= MAX_NODE) {
                // 末道点齐：核讫
                uw.set("status", STATUS_PASS)
                        .set("sign_count", nowSigned);
                r.setStatus(STATUS_PASS);
                r.setSignCount(nowSigned);
            } else {
                // 本口点齐转下一道，新口印数从零起
                uw.set("node_no", node + 1)
                        .set("sign_count", 0);
                r.setNodeNo(node + 1);
                r.setSignCount(0);
            }
        } else {
            // 还缺点印，留在本口
            uw.set("sign_count", nowSigned);
            r.setSignCount(nowSigned);
        }
        uw.set("update_time", new Date());
        if (this.zwStopBillMapper.update(null, uw) <= 0) {
            return null;
        }
        return r;
    }

    /**
     * 否决：在核单直接落"已打回"。已核讫/已打回不许再动；条件更新兜并发。
     */
    @Override
    public TZwStopBill reject(Long id, String approver, String comment) {
        if (id == null || approver == null || approver.trim().isEmpty()) {
            return null;
        }
        TZwStopBill r = this.zwStopBillMapper.selectById(id);
        if (r == null || r.getStatus() == null || r.getStatus() != STATUS_RUNNING) {
            return null;
        }
        UpdateWrapper<TZwStopBill> uw = new UpdateWrapper<TZwStopBill>()
                .set("status", STATUS_VETO)
                .set("update_time", new Date())
                .eq("id", id)
                .eq("status", STATUS_RUNNING)
                .eq("del_flag", 0);
        if (this.zwStopBillMapper.update(null, uw) <= 0) {
            return null;
        }
        r.setStatus(STATUS_VETO);
        return r;
    }

    /**
     * 退回上一环节：在核单退一道，本口已落印作废（清零），上一道重新点齐。
     * 首道无可退、已核讫/已打回拒绝；条件更新兜并发。
     */
    @Override
    public TZwStopBill rollback(Long id, String comment) {
        if (id == null) {
            return null;
        }
        TZwStopBill r = this.zwStopBillMapper.selectById(id);
        if (r == null || r.getNodeNo() == null || r.getStatus() == null
                || r.getStatus() != STATUS_RUNNING) {
            return null;
        }
        int node = r.getNodeNo();
        if (node <= MIN_NODE) {
            return null;
        }
        int signed = r.getSignCount() == null ? 0 : r.getSignCount();
        UpdateWrapper<TZwStopBill> uw = new UpdateWrapper<TZwStopBill>()
                .set("node_no", node - 1)
                .set("sign_count", 0)
                .set("update_time", new Date())
                .eq("id", id)
                .eq("node_no", node)
                .eq("sign_count", signed)
                .eq("status", STATUS_RUNNING)
                .eq("del_flag", 0);
        if (this.zwStopBillMapper.update(null, uw) <= 0) {
            return null;
        }
        r.setNodeNo(node - 1);
        r.setSignCount(0);
        return r;
    }

    /** 本口应到印数：need_count 维护了以它为准，否则按并印办法——两名点齐 2，任一人 1 */
    private static int needOf(TZwStopBill r) {
        if (r.getNeedCount() != null && r.getNeedCount() > 0) {
            return r.getNeedCount();
        }
        return r.getSignMode() != null && r.getSignMode() == MODE_AND ? AND_NEED : OR_NEED;
    }
}
