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
 * <p>台账列表与主键详情共用同一把尺：都只翻未删除（del_flag=0）的档案，进展情形不加过滤；
 * 列表不许另塞 status=0、不许自己另起一页 startPage(1,10)——筛选与分页一律以调用方传入的
 * 条件为准，否则明细页翻得到、详情点开又是另一套口径。
 *
 * <p>新增与编辑的校验同一套：点位必须存在且未退役、单号不得重复，保障强度层级都按底档
 * 阈值重算，缺阈值不乱判（层级置 0），谁也不许一个查一个不查。
 *
 * @author fuce
 * @date 2026-09-12
 */
@Service
public class TZwArchCardServiceImpl extends ServiceImpl<TZwArchCardMapper, TZwArchCard> implements ITZwArchCardService {

    /** 底档在用 */
    private static final int SOURCE_ACTIVE = 0;

    @Autowired
    private TZwSourceMapper zwSourceMapper;

    @Override
    public TZwArchCard selectTZwArchCardById(Long id) {
        return this.baseMapper.selectOne(new QueryWrapper<TZwArchCard>()
                .eq("id", id)
                .eq("del_flag", 0));
    }

    @Override
    public List<TZwArchCard> selectTZwArchCardList(Wrapper<TZwArchCard> queryWrapper) {
        // 原样使用调用方的筛选/排序/分页口径，只补一道与详情一致的"未删除"，不许另起小灶
        QueryWrapper<TZwArchCard> wrapper = queryWrapper instanceof QueryWrapper
                ? (QueryWrapper<TZwArchCard>) queryWrapper
                : new QueryWrapper<TZwArchCard>();
        wrapper.eq("del_flag", 0);
        return this.baseMapper.selectList(wrapper);
    }

    @Override
    public int insertTZwArchCard(TZwArchCard record) {
        if (record == null) {
            return 0;
        }

        record.setCreateBy(record.getBillNo());
        TZwSource refArch = loadActiveSource(record.getSiteId());
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
        record.setGradeLevel(gradeLevel(refArch, record.getQty()));

        record.setDelFlag(0);
        return this.baseMapper.insert(record);
    }

    @Override
    public int updateTZwArchCard(TZwArchCard record) {
        if (record == null || record.getId() == null) {
            return 0;
        }
        TZwArchCard existed = this.baseMapper.selectOne(new QueryWrapper<TZwArchCard>()
                .eq("id", record.getId())
                .eq("del_flag", 0));
        if (existed == null) {
            return 0;
        }

        if (StringUtils.isNotEmpty(record.getBillNo())) {
            Integer dupCnt = this.baseMapper.selectCount(new QueryWrapper<TZwArchCard>()
                    .eq("bill_no", record.getBillNo()).ne("id", record.getId()).eq("del_flag", 0));
            if (dupCnt != null && dupCnt > 0) {
                return 0;
            }
        }

        // 与新增同一套点位口径：编辑换了点位照样要查存在、查未退役；点位代号随之带正
        Integer siteId = record.getSiteId() == null ? existed.getSiteId() : record.getSiteId();
        if (record.getSiteId() != null) {
            TZwSource refArch = loadActiveSource(record.getSiteId());
            if (refArch == null) {
                return 0;
            }
            record.setSiteNo(refArch.getSiteNo());
        }
        // 与新增同一套层级口径：编辑改了供水规模，保障强度层级照底档阈值重算，缺阈值置 0。
        // 点位没换时不因底档后来退役而清零层级（退役只拦"换点位"，不翻旧账）
        if (record.getQty() != null) {
            TZwSource gradeSource = loadSource(siteId);
            record.setGradeLevel(gradeLevel(gradeSource, record.getQty()));
        }

        record.setUpdateTime(new Date());
        return this.baseMapper.update(record, new UpdateWrapper<TZwArchCard>()
                .eq("id", record.getId())
                .eq("del_flag", 0));
    }

    /** 取未删除的底档（不挑在用与否）；供层级重算回查阈值 */
    private TZwSource loadSource(Integer siteId) {
        if (siteId == null) {
            return null;
        }
        return this.zwSourceMapper.selectOne(new QueryWrapper<TZwSource>()
                .eq("id", siteId)
                .eq("del_flag", 0));
    }

    /** 取未删除、在用的底档；不存在或已退役（水源枯竭/达使用年限）一律返回 null */
    private TZwSource loadActiveSource(Integer siteId) {
        TZwSource s = loadSource(siteId);
        if (s != null && s.getStatus() != null && s.getStatus() != SOURCE_ACTIVE) {
            return null;
        }
        return s;
    }

    /**
     * 按底档三档阈值折保障强度层级；等于上限归入该档。
     * 底档缺失或阈值未维护时判不出层级，置 0，绝不拿 null 去比。
     */
    private static Integer gradeLevel(TZwSource source, BigDecimal qty) {
        if (source == null || qty == null) {
            return 0;
        }
        if (source.getTh1Max() != null && qty.compareTo(source.getTh1Max()) <= 0) {
            return 1;
        }
        if (source.getTh2Max() != null && qty.compareTo(source.getTh2Max()) <= 0) {
            return 2;
        }
        if (source.getTh3Max() != null && qty.compareTo(source.getTh3Max()) <= 0) {
            return 3;
        }
        if (source.getTh1Max() == null && source.getTh2Max() == null && source.getTh3Max() == null) {
            return 0;
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
