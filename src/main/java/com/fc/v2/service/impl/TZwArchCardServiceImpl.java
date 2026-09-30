package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fc.v2.common.support.ConvertUtil;
import com.fc.v2.mapper.auto.TZwArchCardMapper;
import com.fc.v2.mapper.auto.TZwSourceMapper;
import com.fc.v2.model.auto.TZwArchCard;
import com.fc.v2.model.auto.TZwSource;
import com.fc.v2.service.ITZwArchCardService;
import com.fc.v2.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 水源地/水厂档案卡Service业务层处理
 *
 * <p>列表（明细页）与详情同一口径：未删（del_flag=0）即都见，进展如何不在查询口暗滤，
 * 也不许在 Service 里硬塞分页（分页归控制器 startPage）。
 * 新增与编辑同一套校验：底档须存在且未退役、卡号不许重、规模分档同一把尺
 * （等于上限算高一档），编辑改了规模要按底档最新阈值重算档位。
 *
 * @author fuce
 * @date 2026-09-12
 */
@Service
public class TZwArchCardServiceImpl extends ServiceImpl<TZwArchCardMapper, TZwArchCard> implements ITZwArchCardService {

    /** 底档在用 / 已退役 */
    private static final int SOURCE_ACTIVE = 0;
    private static final int SOURCE_RETIRED = 1;

    @Autowired
    private TZwSourceMapper zwSourceMapper;

    @Override
    public TZwArchCard selectTZwArchCardById(Long id) {
        return this.baseMapper.selectOne(new QueryWrapper<TZwArchCard>()
                .eq("id", id)
                .eq("del_flag", 0));
    }

    /**
     * 列表：沿用调用方给的条件（台账要哪些进展由控制器/页面定），只统一补一道未删口径，
     * 与 {@link #selectTZwArchCardById} 详情同尺；不在此硬编码 status=0，也不抢分页。
     */
    @Override
    public List<TZwArchCard> selectTZwArchCardList(Wrapper<TZwArchCard> queryWrapper) {
        QueryWrapper<TZwArchCard> qw = queryWrapper instanceof QueryWrapper
                ? (QueryWrapper<TZwArchCard>) queryWrapper
                : new QueryWrapper<TZwArchCard>();
        qw.eq("del_flag", 0);
        return this.baseMapper.selectList(qw);
    }

    @Override
    public int insertTZwArchCard(TZwArchCard record) {
        if (record == null) {
            return 0;
        }

        record.setCreateBy(record.getBillNo());
        TZwSource refArch = requireActiveSource(record.getSiteId());
        if (refArch == null) {
            return 0;
        }
        record.setSiteNo(refArch.getSiteNo());
        if (StringUtils.isNotEmpty(record.getBillNo())) {
            Integer dupCnt = this.baseMapper.selectCount(new QueryWrapper<TZwArchCard>()
                    .eq("bill_no", record.getBillNo()).eq("del_flag", 0));
            if (dupCnt != null && dupCnt > 0) {
                return 0;
            }
        }
        record.setGradeLevel(bandLevel(record.getQty(), refArch));

        record.setDelFlag(0);
        return this.baseMapper.insert(record);
    }

    @Override
    public int updateTZwArchCard(TZwArchCard record) {
        if (record == null || record.getId() == null) {
            return 0;
        }
        TZwArchCard old = this.baseMapper.selectOne(new QueryWrapper<TZwArchCard>()
                .eq("id", record.getId())
                .eq("del_flag", 0));
        if (old == null) {
            return 0;
        }

        if (StringUtils.isNotEmpty(record.getBillNo())) {
            Integer dupCnt = this.baseMapper.selectCount(new QueryWrapper<TZwArchCard>()
                    .eq("bill_no", record.getBillNo()).ne("id", record.getId()).eq("del_flag", 0));
            if (dupCnt != null && dupCnt > 0) {
                return 0;
            }
        }

        // 与新增同口径：底档换了/没换都按未退役的在档底档校验；规模改了按最新阈值重算档位
        Integer siteId = record.getSiteId() != null ? record.getSiteId() : old.getSiteId();
        TZwSource refArch = requireActiveSource(siteId);
        if (refArch == null) {
            return 0;
        }
        record.setSiteNo(refArch.getSiteNo());
        BigDecimal qty = record.getQty() != null ? record.getQty() : old.getQty();
        record.setGradeLevel(bandLevel(qty, refArch));

        record.setUpdateTime(new Date());
        return this.baseMapper.update(record, new UpdateWrapper<TZwArchCard>()
                .eq("id", record.getId())
                .eq("del_flag", 0));
    }

    /** 底档须存在、未删且未退役，否则新增/编辑一律拒（同一把尺） */
    private TZwSource requireActiveSource(Integer siteId) {
        if (siteId == null) {
            return null;
        }
        TZwSource src = this.zwSourceMapper.selectOne(new QueryWrapper<TZwSource>()
                .eq("id", siteId)
                .eq("del_flag", 0));
        if (src == null) {
            return null;
        }
        if (src.getStatus() != null && src.getStatus() == SOURCE_RETIRED) {
            return null;
        }
        return src;
    }

    /**
     * 供水规模折保障强度层级：等于上限算高一档（与底档种子、限值判定线同口径）——
     * 严格小于本档上限才算落本档；上限未维护的档不接读数；规模空按 0 档。
     */
    private static int bandLevel(BigDecimal qty, TZwSource src) {
        if (qty == null) {
            return 0;
        }
        if (src.getTh1Max() != null && qty.compareTo(src.getTh1Max()) < 0) {
            return 1;
        }
        if (src.getTh2Max() != null && qty.compareTo(src.getTh2Max()) < 0) {
            return 2;
        }
        if (src.getTh3Max() != null && qty.compareTo(src.getTh3Max()) < 0) {
            return 3;
        }
        return 4;
    }

    @Override
    public int deleteTZwArchCardByIds(String ids) {
        Long[] idArr = ConvertUtil.toLongArray(ids);
        return this.baseMapper.deleteBatchIds(Arrays.asList(idArr));
    }

    @Override
    public int deleteTZwArchCardById(Long id) {
        return this.baseMapper.deleteById(id);
    }
}
