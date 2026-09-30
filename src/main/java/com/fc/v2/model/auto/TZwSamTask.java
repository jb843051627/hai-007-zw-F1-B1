package com.fc.v2.model.auto;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 采样任务到期派单对象 t_zw_sam_task
 *
 * @author fuce
 * @date 2026-09-12
 */
@TableName("t_zw_sam_task")
@ApiModel(value = "TZwSamTask", description = "采样任务到期派单")
public class TZwSamTask implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 采样事项号（计划代号-采样日，系统一次生成） */
    @TableField("item_no")
    @ApiModelProperty(value = "采样事项号")
    private String itemNo;

    /** 检测计划周期代号（底档带入） */
    @TableField("plan_no")
    @ApiModelProperty(value = "检测计划周期代号")
    private String planNo;

    /** 点位代号（底档带入） */
    @TableField("site_no")
    @ApiModelProperty(value = "点位代号")
    private String siteNo;

    /** 承接班组(部门主键)；空=派发时按当前负载加权分派后回写 */
    @JsonSerialize(using = ToStringSerializer.class)
    @TableField("team_id")
    @ApiModelProperty(value = "承接班组")
    private Long teamId;

    /** 约定采样那一天的终结时刻(当日23:59:59)——全站唯一口径，任何动作不得挪动 */
    @TableField("due_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "约定采样那一天的终结时刻")
    private Date dueAt;

    /** 提前浮出待办的自然日数（唯一可调档） */
    @TableField("amount")
    @ApiModelProperty(value = "提前浮出待办的自然日数")
    private BigDecimal amount;

    /** 真正派出时刻（首次钉库，后到动作不得覆盖） */
    @TableField("dispatch_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "真正派出时刻")
    private Date dispatchAt;

    /** 撤单时刻 */
    @TableField("cancel_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "撤单时刻")
    private Date cancelAt;

    /** 采样事由与受派班组记要 */
    @TableField("content")
    @ApiModelProperty(value = "采样事由与受派班组记要")
    private String content;

    /** 挂起原因（点位退役/班组停班/无班可派） */
    @TableField("hang_reason")
    @ApiModelProperty(value = "挂起原因")
    private String hangReason;

    /** 条目情形 0待派 1已派 2挂起 3撤单 4转作他理 */
    @TableField("status")
    @ApiModelProperty(value = "条目情形 0待派 1已派 2挂起 3撤单 4转作他理")
    private Integer status;

    /** 删除标记 0正常 1删除 */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记 0正常 1删除")
    private Integer delFlag;

    /** 创建者 */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    /** 更新者 */
    @TableField(value = "update_by", fill = FieldFill.UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

    /** 备注 */
    @TableField("remark")
    @ApiModelProperty(value = "备注")
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getItemNo() {
        return itemNo;
    }

    public void setItemNo(String itemNo) {
        this.itemNo = itemNo;
    }

    public String getPlanNo() {
        return planNo;
    }

    public void setPlanNo(String planNo) {
        this.planNo = planNo;
    }

    public String getSiteNo() {
        return siteNo;
    }

    public void setSiteNo(String siteNo) {
        this.siteNo = siteNo;
    }

    public Long getTeamId() {
        return teamId;
    }

    public void setTeamId(Long teamId) {
        this.teamId = teamId;
    }

    public Date getDueAt() {
        return dueAt;
    }

    public void setDueAt(Date dueAt) {
        this.dueAt = dueAt;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Date getDispatchAt() {
        return dispatchAt;
    }

    public void setDispatchAt(Date dispatchAt) {
        this.dispatchAt = dispatchAt;
    }

    public Date getCancelAt() {
        return cancelAt;
    }

    public void setCancelAt(Date cancelAt) {
        this.cancelAt = cancelAt;
    }

    public String getHangReason() {
        return hangReason;
    }

    public void setHangReason(String hangReason) {
        this.hangReason = hangReason;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Integer delFlag) {
        this.delFlag = delFlag;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
