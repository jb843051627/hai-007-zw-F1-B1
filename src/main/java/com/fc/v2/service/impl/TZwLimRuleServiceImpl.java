package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TZwLimRuleMapper;
import com.fc.v2.model.auto.TZwLimRule;
import com.fc.v2.service.ITZwLimRuleService;

/**
 * 水质指标限值分档判定线 Service业务层处理（rule-eval 形状）
 *
 * <p>"某条判定线在 at 时刻现行可用"全站只有 {@link #effective} 一把尺：
 * del_flag=0、情形为现行（status=0）、at 落在启用区间 [eff_start, eff_end)（启用之日含、交棒之日不含）。
 * 列表 {@link #listAvailable}、单规则定位 {@link #findRule}、旁路可用性 {@link #usable} 与角标
 * {@link #countAvailable} 全部过这同一谓词，禁止各写各的。
 *
 * <p>区间比较一律在内存里按墙钟毫秒做，不把 java.util.Date 绑进 SQL：
 * JDBC 的 serverTimezone 与本机时区不对称时，"切换当日"这类边界会整体错档（同域限值线已踩过）。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TZwLimRuleServiceImpl implements ITZwLimRuleService {

    /** 判定线情形 0现行 1已让位 */
    private static final int STATUS_CURRENT = 0;

    /** 入参合法量域（与接口约定一致：越界一律按不可判处理） */
    private static final BigDecimal INPUT_MIN = BigDecimal.ZERO;
    private static final BigDecimal INPUT_MAX = new BigDecimal("100");

    /** 优先级降序；并列按 ruleCode 降序（确定性） */
    private static final Comparator<TZwLimRule> PRIORITY_THEN_CODE_DESC = new Comparator<TZwLimRule>() {
        @Override
        public int compare(TZwLimRule a, TZwLimRule b) {
            int pa = a.getPriority() == null ? 0 : a.getPriority();
            int pb = b.getPriority() == null ? 0 : b.getPriority();
            if (pa != pb) {
                return pb - pa;
            }
            String ca = a.getRuleCode() == null ? "" : a.getRuleCode();
            String cb = b.getRuleCode() == null ? "" : b.getRuleCode();
            return cb.compareTo(ca);
        }
    };

    @javax.annotation.Resource
    private TZwLimRuleMapper zwLimRuleMapper;

    @Override
    public TZwLimRule selectTZwLimRuleById(Long id) {
        return this.zwLimRuleMapper.selectById(id);
    }

    @Override
    public List<TZwLimRule> listAvailable(Date at) {
        if (at == null) {
            return Collections.emptyList();
        }
        List<TZwLimRule> all = this.zwLimRuleMapper.selectList(new QueryWrapper<TZwLimRule>()
                .eq("del_flag", 0));
        java.util.List<TZwLimRule> avail = new java.util.ArrayList<TZwLimRule>();
        for (TZwLimRule r : all) {
            if (effective(r, at)) {
                avail.add(r);
            }
        }
        // 列表顺序就是"谁先说话"的顺序，evaluateTop 直接取首位，与台账同口径
        Collections.sort(avail, PRIORITY_THEN_CODE_DESC);
        return avail;
    }

    @Override
    public int evaluate(String ruleCode, BigDecimal input, Date at) {
        if (ruleCode == null || ruleCode.trim().isEmpty() || !inputInRange(input) || at == null) {
            return 0;
        }
        TZwLimRule rule = findRule(ruleCode, at);
        if (rule == null) {
            return 0;
        }
        return levelOf(rule, input);
    }

    @Override
    public int evaluateTop(BigDecimal input, Date at) {
        // 缺参/越界口径与单规则求值完全一致，不许一个挡一个不挡
        if (!inputInRange(input) || at == null) {
            return 0;
        }
        List<TZwLimRule> avail = listAvailable(at);
        if (avail.isEmpty()) {
            return 0;
        }
        // avail 已按优先级降序排好，首位即"数值大的先说话"那条
        return levelOf(avail.get(0), input);
    }

    @Override
    public boolean usable(Long id, Date at) {
        if (id == null || at == null) {
            return false;
        }
        // 与列表/定位同谓词：已删除、已让位、时段外都不可用
        return effective(this.zwLimRuleMapper.selectById(id), at);
    }

    @Override
    public int countAvailable(Date at) {
        return listAvailable(at).size();
    }

    /**
     * 唯一的"现行可用"谓词：未删除、情形为现行、at 落在 [eff_start, eff_end)。
     * 起止皆空表示长期有效；只空一头表示单侧开放。
     */
    private static boolean effective(TZwLimRule r, Date at) {
        if (r == null || at == null) {
            return false;
        }
        if (r.getDelFlag() != null && r.getDelFlag() != 0) {
            return false;
        }
        if (r.getStatus() == null || r.getStatus() != STATUS_CURRENT) {
            return false;
        }
        long t = at.getTime();
        if (r.getEffStart() != null && t < r.getEffStart().getTime()) {
            return false;
        }
        // 交棒之日不含：恰在交棒日 00:00:00 即让位
        if (r.getEffEnd() != null && t >= r.getEffEnd().getTime()) {
            return false;
        }
        return true;
    }

    private TZwLimRule findRule(String ruleCode, Date at) {
        List<TZwLimRule> hit = this.zwLimRuleMapper.selectList(new QueryWrapper<TZwLimRule>()
                .eq("del_flag", 0)
                .eq("rule_code", ruleCode));
        TZwLimRule picked = null;
        for (TZwLimRule r : hit) {
            if (!effective(r, at)) {
                continue;
            }
            // 同号版本理论上不该时段重叠；万一重叠，启用之日晚的视为新版本（确定性：再并列取主键大的）
            if (picked == null
                    || (r.getEffStart() != null && (picked.getEffStart() == null
                        || r.getEffStart().after(picked.getEffStart())))
                    || (r.getEffStart() != null && picked.getEffStart() != null
                        && r.getEffStart().getTime() == picked.getEffStart().getTime()
                        && r.getId() > picked.getId())) {
                picked = r;
            }
        }
        return picked;
    }

    /** 缺参或越界（&lt;0 或 &gt;100）都不可判 */
    private static boolean inputInRange(BigDecimal input) {
        return input != null
                && input.compareTo(INPUT_MIN) >= 0
                && input.compareTo(INPUT_MAX) <= 0;
    }

    /** 分档：等于上限取高一档（恰为 th1Max 入 2 档，恰为 th3Max 入 4 档）；缺档阈值向高档顺延；三档全缺判不了返回 0 */
    private int levelOf(TZwLimRule rule, BigDecimal input) {
        if (rule.getTh1Max() == null && rule.getTh2Max() == null && rule.getTh3Max() == null) {
            return 0;
        }
        if (rule.getTh1Max() != null && input.compareTo(rule.getTh1Max()) < 0) {
            return 1;
        }
        if (rule.getTh2Max() != null && input.compareTo(rule.getTh2Max()) < 0) {
            return 2;
        }
        if (rule.getTh3Max() != null && input.compareTo(rule.getTh3Max()) < 0) {
            return 3;
        }
        return 4;
    }
}
