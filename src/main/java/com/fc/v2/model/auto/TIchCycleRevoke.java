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
import java.util.Date;

/**
 * 履约考核到期单撤回留痕对象 t_ich_cycle_revoke
 *
 * <p>越过该出手那日还没送出去的行子，把话撤回时另记一笔，事后查得到它的存在；
 * 快照列只留底，不回盖到期单上的 due_at 与 done_at 两处旧字。
 *
 * @author fuce
 * @date 2026-10-02
 */
@TableName("t_ich_cycle_revoke")
@ApiModel(value = "TIchCycleRevoke", description = "履约考核到期单撤回留痕")
public class TIchCycleRevoke implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 所属到期单 t_ich_cycle_task.id */
    @TableField("task_id")
    @ApiModelProperty(value = "所属到期单 t_ich_cycle_task.id")
    private Long taskId;

    /** 到期单号（冗余直出） */
    @TableField("item_no")
    @ApiModelProperty(value = "到期单号（冗余直出）")
    private String itemNo;

    /** 撤回那一刻 */
    @TableField("revoke_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "撤回那一刻")
    private Date revokeAt;

    /** 撤回缘由 */
    @TableField("reason")
    @ApiModelProperty(value = "撤回缘由")
    private String reason;

    /** 头回定下的那一日（快照留底，不回盖） */
    @TableField("due_at_snapshot")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "头回定下的那一日（快照留底，不回盖）")
    private Date dueAtSnapshot;

    /** 上一回真办成的那一刻（快照留底，不回盖） */
    @TableField("last_done_at_snapshot")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "上一回真办成的那一刻（快照留底，不回盖）")
    private Date lastDoneAtSnapshot;

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

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public String getItemNo() {
        return itemNo;
    }

    public void setItemNo(String itemNo) {
        this.itemNo = itemNo;
    }

    public Date getRevokeAt() {
        return revokeAt;
    }

    public void setRevokeAt(Date revokeAt) {
        this.revokeAt = revokeAt;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Date getDueAtSnapshot() {
        return dueAtSnapshot;
    }

    public void setDueAtSnapshot(Date dueAtSnapshot) {
        this.dueAtSnapshot = dueAtSnapshot;
    }

    public Date getLastDoneAtSnapshot() {
        return lastDoneAtSnapshot;
    }

    public void setLastDoneAtSnapshot(Date lastDoneAtSnapshot) {
        this.lastDoneAtSnapshot = lastDoneAtSnapshot;
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
