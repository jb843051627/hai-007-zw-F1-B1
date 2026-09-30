package com.fc.v2.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TZwImpRowMapper;
import com.fc.v2.model.auto.TZwImpRow;
import com.fc.v2.service.ITZwImpRowService;

/**
 * 检测数据导入核对行 Service业务层处理（batch-process 形状：整批提交）
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

    @Override
    public int submitBatch(String batchNo, List<TZwImpRow> rows) {
        String no = rows.get(0).getBatchNo();
        java.util.List<TZwImpRow> errors = new java.util.ArrayList<TZwImpRow>();
        int seq = 0;
        for (TZwImpRow r : rows) {
            if (r.getItemCode() == null || r.getItemCode().trim().isEmpty()
                    || r.getQty() == null
                    || r.getQty().compareTo(java.math.BigDecimal.ZERO) <= 0) {
                seq++;
                r.setRowNo(Integer.valueOf(seq));
                r.setBatchNo(no);
                r.setStatus(STATUS_FAIL);
                this.zwImpRowMapper.insert(r);
                errors.add(r);
            }
        }
        if (!errors.isEmpty()) {
            return 0;
        }
        int ok = 0;
        for (TZwImpRow r : rows) {
            r.setBatchNo(no);
            r.setStatus(STATUS_OK);
            this.zwImpRowMapper.insert(r);
            ok++;
        }
        return ok;
    }

    @Override
    public List<TZwImpRow> listErrors(String batchNo) {
        return this.zwImpRowMapper.selectList(new QueryWrapper<TZwImpRow>()
                .eq("batch_no", batchNo).eq("status", STATUS_FAIL));
    }
}
