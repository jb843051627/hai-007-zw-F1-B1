package com.fc.v2.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TZwImpRowMapper;
import com.fc.v2.model.auto.TZwImpRow;
import com.fc.v2.service.ITZwImpRowService;

import java.math.BigDecimal;
import java.util.ArrayList;

/**
 * 检测数据导入核对行 Service业务层处理（batch-process 形状：整批提交）
 *
 * <p>逐行过校验：读数项代号空、读数空、读数非正的行落"退回"明细（保留原始行号），
 * 其余落"已入库"，<b>坏一行只记那一行，不许把整批卡死</b>。同一批次重复提交幂等：
 * 已有落地行的批次直接回现有成功数，一行都不重复落。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TZwImpRowServiceImpl implements ITZwImpRowService {

    private static final int MAX_ROWS = 500;
    private static final int STATUS_WAIT = 0;
    private static final int STATUS_OK = 1;
    private static final int STATUS_FAIL = 2;

    @javax.annotation.Resource
    private TZwImpRowMapper zwImpRowMapper;

    @Override
    public TZwImpRow selectTZwImpRowById(Long id) {
        return this.zwImpRowMapper.selectById(id);
    }

    /**
     * 整批提交：
     * <ol>
     *   <li>空批次 → 0（没有可处理的行不是执行出错）；</li>
     *   <li>该批次已提交过 → 幂等返回已入库行数，不重复落一行；</li>
     *   <li>行数超 {@value #MAX_ROWS} → -1，整批不入库；</li>
     *   <li>逐行校验：合法落已入库、非法落退回明细（行号照旧），返回成功行数。</li>
     * </ol>
     * 入库同批一次事务：中途库层炸掉整批回滚，不会留半截账。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int submitBatch(String batchNo, List<TZwImpRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        String no = batchNo == null || batchNo.trim().isEmpty() ? rows.get(0).getBatchNo() : batchNo;
        if (no == null || no.trim().isEmpty()) {
            return 0;
        }

        Integer existed = this.zwImpRowMapper.selectCount(new QueryWrapper<TZwImpRow>()
                .eq("batch_no", no)
                .in("status", STATUS_OK, STATUS_FAIL)
                .eq("del_flag", 0));
        if (existed != null && existed > 0) {
            Integer ok = this.zwImpRowMapper.selectCount(new QueryWrapper<TZwImpRow>()
                    .eq("batch_no", no)
                    .eq("status", STATUS_OK)
                    .eq("del_flag", 0));
            return ok == null ? 0 : ok;
        }

        if (rows.size() > MAX_ROWS) {
            return -1;
        }

        int seq = 0;
        int okCount = 0;
        for (TZwImpRow r : rows) {
            seq++;
            r.setBatchNo(no);
            // 行号照旧：上游没给才按本批次序补，纸上对号全靠它
            if (r.getRowNo() == null) {
                r.setRowNo(seq);
            }
            r.setDelFlag(0);
            if (isBad(r)) {
                r.setStatus(STATUS_FAIL);
            } else {
                r.setStatus(STATUS_OK);
                okCount++;
            }
            this.zwImpRowMapper.insert(r);
        }
        return okCount;
    }

    /** 坏户口径（新增/重交同一把尺）：代号空、读数空、读数非正 */
    private static boolean isBad(TZwImpRow r) {
        return r.getItemCode() == null || r.getItemCode().trim().isEmpty()
                || r.getQty() == null
                || r.getQty().compareTo(BigDecimal.ZERO) <= 0;
    }

    /** 该批次的退回明细（按行号升序，null 行号排最后） */
    @Override
    public List<TZwImpRow> listErrors(String batchNo) {
        if (batchNo == null || batchNo.trim().isEmpty()) {
            return new ArrayList<TZwImpRow>();
        }
        return this.zwImpRowMapper.selectList(new QueryWrapper<TZwImpRow>()
                .eq("batch_no", batchNo)
                .eq("status", STATUS_FAIL)
                .eq("del_flag", 0)
                .orderByAsc("row_no"));
    }
}
