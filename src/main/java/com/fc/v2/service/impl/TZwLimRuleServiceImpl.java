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
 * <p>"此刻生效"全站只有一个口径：现行（status=0）、未删、启用之日 ≤ at（空=不限）、
 * 交棒之日 &gt; at（空=长期现行，交棒日不含）。定位版本、可用列表、单条可用、
 * 角标计数全从 {@link #effectiveBase} 这一把尺出来，谁也不许另写一份。
 *
 * <p>分档边界与底档种子同口径：<b>等于上限取高一档</b>（input &lt; 一档上限才算一档）。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TZwLimRuleServiceImpl implements ITZwLimRuleService {

    /** 判定线现行 */
    private static final int STATUS_ACTIVE = 0;

    /** 读数合法区间（含两端）：越界按不可判处理 */
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

    /**
     * 时段比较统一走**墙钟字符串**（yyyy-MM-dd HH:mm:ss）+ 字符串绑定：
     * JDBC 的 serverTimezone 与本机时区不对称，直接把 java.util.Date 作参数会整体偏移，
     * 使边界判定在"切换当日"这类用例上随机错档（实测踩到）。
     */
    private static String ts(Date d) {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d);
    }

    /**
     * 全站唯一生效口径：[eff_start, eff_end)，两端都可空（空=不设界），交棒日不含。
     */
    private QueryWrapper<TZwLimRule> effectiveBase(Date at) {
        String now = ts(at);
        return new QueryWrapper<TZwLimRule>()
                .eq("del_flag", 0)
                .eq("status", STATUS_ACTIVE)
                .and(w -> w.isNull("eff_start").or().le("eff_start", now))
                .and(w -> w.isNull("eff_end").or().gt("eff_end", now));
    }

    @Override
    public TZwLimRule selectTZwLimRuleById(Long id) {
        return this.zwLimRuleMapper.selectById(id);
    }

    @Override
    public List<TZwLimRule> listAvailable(Date at) {
        if (at == null) {
            return new java.util.ArrayList<TZwLimRule>();
        }
        // SQL 先按生效区间收一道，Java 侧再用同一谓词复核——两边一把尺，不依赖驱动时区行为
        List<TZwLimRule> all = this.zwLimRuleMapper.selectList(effectiveBase(at));
        List<TZwLimRule> list = new java.util.ArrayList<TZwLimRule>();
        for (TZwLimRule r : all) {
            if (isEffective(r, at)) {
                list.add(r);
            }
        }
        Collections.sort(list, PRIORITY_THEN_CODE_DESC);
        return list;
    }

    @Override
    public int evaluate(String ruleCode, BigDecimal input, Date at) {
        if (ruleCode == null || ruleCode.trim().isEmpty() || !inRange(input) || at == null) {
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
        if (!inRange(input) || at == null) {
            return 0;
        }
        List<TZwLimRule> avail = listAvailable(at);
        if (avail.isEmpty()) {
            return 0;
        }
        // 列表已按优先级降序排好，头一条就是最该说话的判定线
        return levelOf(avail.get(0), input);
    }

    @Override
    public boolean usable(Long id, Date at) {
        if (id == null || at == null) {
            return false;
        }
        TZwLimRule r = this.zwLimRuleMapper.selectById(id);
        return r != null && isEffective(r, at);
    }

    @Override
    public int countAvailable(Date at) {
        if (at == null) {
            return 0;
        }
        // 角标与列表同出 listAvailable 一次判定，不许一个走 SQL 一个走 Java 数出两个数
        return listAvailable(at).size();
    }

    /** 同代号多版本时取启用之日最晚的那版（与列表/单条同一套生效谓词） */
    private TZwLimRule findRule(String ruleCode, Date at) {
        List<TZwLimRule> hit = this.zwLimRuleMapper.selectList(
                effectiveBase(at).eq("rule_code", ruleCode));
        TZwLimRule newest = null;
        for (TZwLimRule r : hit) {
            if (!isEffective(r, at)) {
                continue;
            }
            if (newest == null
                    || effStartEpoch(r) > effStartEpoch(newest)) {
                newest = r;
            }
        }
        return newest;
    }

    private static long effStartEpoch(TZwLimRule r) {
        return r.getEffStart() == null ? Long.MIN_VALUE : r.getEffStart().getTime();
    }

    /** 生效谓词的 Java 侧版本，供单条可用判定复用，口径与 {@link #effectiveBase} 完全一致 */
    private static boolean isEffective(TZwLimRule r, Date at) {
        if (r.getStatus() == null || r.getStatus() != STATUS_ACTIVE) {
            return false;
        }
        if (r.getDelFlag() != null && r.getDelFlag() != 0) {
            return false;
        }
        long now = at.getTime();
        if (r.getEffStart() != null && r.getEffStart().getTime() > now) {
            return false;
        }
        if (r.getEffEnd() != null && r.getEffEnd().getTime() <= now) {
            return false;
        }
        return true;
    }

    private static boolean inRange(BigDecimal input) {
        return input != null
                && input.compareTo(INPUT_MIN) >= 0
                && input.compareTo(INPUT_MAX) <= 0;
    }

    /**
     * 分档：等于上限取高一档——input 严格小于本档上限才算落本档；
     * 某档上限没维护时该档不接读数，往下一档看；三档都兜不住即 4（严重超标之上）。
     */
    private int levelOf(TZwLimRule rule, BigDecimal input) {
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
