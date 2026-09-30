package com.fc.v2.model.custom.health;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 健康判定回执。
 * available=false 表示那一刻一版判定线都套不上（当下没有可用判定线），
 * level 恒为 0，调用方不得把它当成某个档位；命中时 level 为 1..4，
 * 并回显命中的那一版（代号/名称/窗口/取优序号），定责两边照此对账。
 *
 * @author fuce
 * @date 2026-09-23
 */
@ApiModel(value = "HealthEvalResult", description = "健康判定回执")
public class HealthEvalResult implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 是否命中现行判定线（false=当下没有可用判定线） */
    @ApiModelProperty(value = "是否命中现行判定线")
    private boolean available;

    /** 判定档位 1..4；未命中恒为 0（不是档位） */
    @ApiModelProperty(value = "判定档位 1..4，未命中为 0")
    private int level;

    /** 机体类别（入参回显） */
    @ApiModelProperty(value = "机体类别")
    private String siteType;

    /** 分系统（入参回显） */
    @ApiModelProperty(value = "分系统")
    private String subSystem;

    /** 判定数值（入参回显） */
    @ApiModelProperty(value = "判定数值")
    private BigDecimal input;

    /** 判定时刻（入参回显） */
    @ApiModelProperty(value = "判定时刻")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date at;

    /** 命中判定线代号 */
    @ApiModelProperty(value = "命中判定线代号")
    private String ruleCode;

    /** 命中判定线名称 */
    @ApiModelProperty(value = "命中判定线名称")
    private String ruleName;

    /** 命中版实施起始时刻 */
    @ApiModelProperty(value = "命中版实施起始时刻")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date effStart;

    /** 命中版被代替截止时刻(不含)，空表示至今仍现行 */
    @ApiModelProperty(value = "命中版被代替截止时刻(不含)")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date effEnd;

    /** 命中版取优序号 */
    @ApiModelProperty(value = "命中版取优序号")
    private Integer priority;

    /** 提示语（未命中/被挡都直说） */
    @ApiModelProperty(value = "提示语")
    private String message;

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public String getSiteType() {
        return siteType;
    }

    public void setSiteType(String siteType) {
        this.siteType = siteType;
    }

    public String getSubSystem() {
        return subSystem;
    }

    public void setSubSystem(String subSystem) {
        this.subSystem = subSystem;
    }

    public BigDecimal getInput() {
        return input;
    }

    public void setInput(BigDecimal input) {
        this.input = input;
    }

    public Date getAt() {
        return at;
    }

    public void setAt(Date at) {
        this.at = at;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public Date getEffStart() {
        return effStart;
    }

    public void setEffStart(Date effStart) {
        this.effStart = effStart;
    }

    public Date getEffEnd() {
        return effEnd;
    }

    public void setEffEnd(Date effEnd) {
        this.effEnd = effEnd;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
