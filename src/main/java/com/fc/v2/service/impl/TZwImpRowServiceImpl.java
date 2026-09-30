package com.fc.v2.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TZwImpRowMapper;
import com.fc.v2.model.auto.TZwImpRow;
import com.fc.v2.service.ITZwImpRowService;

/**
 * 检测数据导入核对行 Service业务层处理（batch-process 形状：整批提交）
 *
 * <p>逐行校验：合法行入正式账、非法行入失败明细（保留原始行号），谁也不连累谁——
 * 坏一行不许让整批合法数据停在库外。整批在一个事务内落账，库级异常整体回滚。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TZwImpRowServiceImpl implements ITZwImpRowService {

    private static final int MAX_ROWS = 500;
    private static final int STATUS_OK = 1;
    private static final int STATUS_FAIL = 2;

    @javax.annotation.Resource
    private TZwImpRowMapper zwImpRowMapper;

    @Override
    public TZwImpRow selectTZwImpRowById(Long id) {
        return this.zwImpRowMapper.selectById(id);
    }

    /**
     * 整批提交。
     * <ul>
     *   <li>空批次 → 0，不当成执行出错；</li>
     *   <li>行数超 {@link #MAX_ROWS} → -1，整批一笔不入库；</li>
     *   <li>同批次号重复提交幂等：已落过账直接返回既有成功行数，不重复入库；</li>
     *   <li>逐行校验：合法行置 OK 入库，非法行（事项编号空 / 数量空或非正）置 FAIL 入失败明细，
     *       行号保留原始序号；返回成功行数。</li>
     * </ul>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int submitBatch(String batchNo, List<TZwImpRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        if (rows.size() > MAX_ROWS) {
            return -1;
        }
        String no = batchNo == null || batchNo.trim().isEmpty() ? rows.get(0).getBatchNo() : batchNo;
        // 同批次重复提交：已有账就原样返回成功行数，绝不重复落
        Integer existed = this.zwImpRowMapper.selectCount(new QueryWrapper<TZwImpRow>()
                .eq("batch_no", no)
                .eq("del_flag", 0));
        if (existed != null && existed > 0) {
            Integer okExisted = this.zwImpRowMapper.selectCount(new QueryWrapper<TZwImpRow>()
                    .eq("batch_no", no)
                    .eq("status", STATUS_OK)
                    .eq("del_flag", 0));
            return okExisted == null ? 0 : okExisted;
        }
        int ok = 0;
        for (int i = 0; i < rows.size(); i++) {
            TZwImpRow r = rows.get(i);
            r.setRowNo(Integer.valueOf(i + 1));
            r.setBatchNo(no);
            r.setDelFlag(0);
            if (isInvalid(r)) {
                // 坏一行只落一笔失败明细，合法行继续过账——不许整批停摆
                r.setStatus(STATUS_FAIL);
            } else {
                r.setStatus(STATUS_OK);
                ok++;
            }
            this.zwImpRowMapper.insert(r);
        }
        return ok;
    }

    /** 事项编号空、数量空或非正，都算非法行 */
    private static boolean isInvalid(TZwImpRow r) {
        return r.getItemCode() == null || r.getItemCode().trim().isEmpty()
                || r.getQty() == null
                || r.getQty().compareTo(java.math.BigDecimal.ZERO) <= 0;
    }

    @Override
    public List<TZwImpRow> listErrors(String batchNo) {
        return this.zwImpRowMapper.selectList(new QueryWrapper<TZwImpRow>()
                .eq("batch_no", batchNo)
                .eq("status", STATUS_FAIL)
                .eq("del_flag", 0)
                .orderByAsc("row_no"));
    }
}
