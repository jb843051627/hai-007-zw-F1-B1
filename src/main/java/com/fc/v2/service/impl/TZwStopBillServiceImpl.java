package com.fc.v2.service.impl;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.mapper.auto.TZwStopBillMapper;
import com.fc.v2.model.auto.TZwStopBill;
import com.fc.v2.service.ITZwStopBillService;

/**
 * 停供/限供申请签核单 Service业务层处理（approval-chain 形状：多阶段签批）
 *
 * <p>报批情形（在核/已核讫/已打回）与当前所在道、本口落印数永远同一条更新落定，
 * 谁也不许只挪一样：否决必落"已打回"，退回必改回"在核"，核讫必连同最后一道一起钉。
 * 只有"在核"的单收票——已核讫、已打回的不再参与签批，杜绝处理完又被拉进来签一遍。
 * 全部走带"当前道/报批情形"条件的原子更新，并发签批与退回撞车时 0 行即被拒，两边同口径。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TZwStopBillServiceImpl implements ITZwStopBillService {

    /** 道次：业务提 / 水质核 / 调度准，推进到该道即全部核讫 */
    private static final int MAX_NODE = 2;
    private static final int MODE_OR = 0;
    private static final int MODE_AND = 1;
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
     * 签批一票。
     * 任一人制：一票即进下一道；两名点齐制：本口落印数 +1，未点齐留在本口继续在核，点齐才进道。
     * 推进到调度准（最后一道）即落"已核讫"。非"在核"单、越道签批一律被拒。
     */
    @Override
    public TZwStopBill approve(Long id, String approver, String comment) {
        TZwStopBill r = this.zwStopBillMapper.selectById(id);
        if (r == null || approver == null || approver.trim().isEmpty()) {
            return null;
        }
        // 只收在核的票：已核讫的不再签，已打回的须先退回环节
        if (r.getStatus() == null || r.getStatus() != STATUS_RUNNING) {
            return null;
        }
        int node = r.getNodeNo() == null ? 0 : r.getNodeNo();
        if (node >= MAX_NODE) {
            return null;
        }
        int mode = r.getSignMode() == null ? MODE_OR : r.getSignMode();
        int signed = r.getSignCount() == null ? 0 : r.getSignCount();
        int need = r.getNeedCount() == null ? 1 : Math.max(1, r.getNeedCount().intValue());

        int nextNode;
        int nextSigned;
        int nextStatus;
        if (mode == MODE_AND && signed + 1 < need) {
            // 两名未点齐：留在本口，继续在核
            nextNode = node;
            nextSigned = signed + 1;
            nextStatus = STATUS_RUNNING;
        } else {
            // 本口点齐（或任一人制）：印数清零后进下一道；到调度准即核讫
            nextNode = node + 1;
            nextSigned = 0;
            nextStatus = nextNode >= MAX_NODE ? STATUS_PASS : STATUS_RUNNING;
        }
        int rows = this.zwStopBillMapper.update(null, new UpdateWrapper<TZwStopBill>()
                .set("node_no", nextNode)
                .set("sign_count", nextSigned)
                // 道次、落印数、报批情形三样一次钉齐，缺一样都不算成
                .set("status", nextStatus)
                .set("remark", appendTrace(r.getRemark(),
                        "同意 " + ts() + " " + approver + " " + safe(comment)))
                .eq("id", id)
                .eq("node_no", node)
                .eq("status", STATUS_RUNNING)
                .eq("del_flag", 0));
        return rows > 0 ? this.zwStopBillMapper.selectById(id) : null;
    }

    /**
     * 否决：落"已打回"，单停在被否的那一道留痕；之后须走退回环节改回在核才能再签。
     * 非"在核"单（含已打回）重复否决一律被拒。
     */
    @Override
    public TZwStopBill reject(Long id, String approver, String comment) {
        TZwStopBill r = this.zwStopBillMapper.selectById(id);
        if (r == null || approver == null || approver.trim().isEmpty()) {
            return null;
        }
        if (r.getStatus() == null || r.getStatus() != STATUS_RUNNING) {
            return null;
        }
        int rows = this.zwStopBillMapper.update(null, new UpdateWrapper<TZwStopBill>()
                // 否决必须把报批情形落成"已打回"——不许只留痕不落状态，让单子还挂在核里被重复签
                .set("status", STATUS_VETO)
                .set("remark", appendTrace(r.getRemark(),
                        "打回 " + ts() + " " + approver + " " + safe(comment)))
                .eq("id", id)
                .eq("status", STATUS_RUNNING)
                .eq("del_flag", 0));
        return rows > 0 ? this.zwStopBillMapper.selectById(id) : null;
    }

    /**
     * 退回上一环节：道次退一格、本口落印数清零、报批情形一并改回"在核"——
     * 不许只退道次还留着"已核讫/已打回"，造成两边口径对不上。第一道无可退，被拒。
     */
    @Override
    public TZwStopBill rollback(Long id, String comment) {
        TZwStopBill r = this.zwStopBillMapper.selectById(id);
        if (r == null) {
            return null;
        }
        int node = r.getNodeNo() == null ? 0 : r.getNodeNo();
        if (node <= 0) {
            return null;
        }
        int rows = this.zwStopBillMapper.update(null, new UpdateWrapper<TZwStopBill>()
                .set("node_no", node - 1)
                .set("sign_count", 0)
                // 退回与改回在核同一条更新，超时重试/回滚后两边看到的都是"上一道在核"
                .set("status", STATUS_RUNNING)
                .set("remark", appendTrace(r.getRemark(), "退回 " + ts() + " " + safe(comment)))
                .eq("id", id)
                .eq("node_no", node)
                .eq("del_flag", 0));
        return rows > 0 ? this.zwStopBillMapper.selectById(id) : null;
    }

    private static String appendTrace(String old, String line) {
        if (old == null || old.isEmpty()) {
            return line;
        }
        return old + "；" + line;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String ts() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }
}
