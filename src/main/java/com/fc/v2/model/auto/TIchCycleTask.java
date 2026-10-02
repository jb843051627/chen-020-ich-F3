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
 * 履约考核到期单对象 t_ich_cycle_task
 *
 * @author fuce
 * @date 2026-09-12
 */
@TableName("t_ich_cycle_task")
@ApiModel(value = "TIchCycleTask", description = "履约考核到期单")
public class TIchCycleTask implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 履约考核到期单 */
    @TableField("item_no")
    @ApiModelProperty(value = "履约考核到期单")
    private String itemNo;

    /** 事由 0履约催办 1年度考核 */
    @TableField("biz_type")
    @ApiModelProperty(value = "事由 0履约催办 1年度考核")
    private Integer bizType;

    /** 拴在哪 0名录项目 1传承人 */
    @TableField("target_type")
    @ApiModelProperty(value = "拴在哪 0名录项目 1传承人")
    private Integer targetType;

    /** 名录项目代号或传承人代号 */
    @TableField("target_no")
    @ApiModelProperty(value = "名录项目代号或传承人代号")
    private String targetNo;

    /** 所对名录项目或传承人称呼 */
    @TableField("target_name")
    @ApiModelProperty(value = "所对名录项目或传承人称呼")
    private String targetName;

    /** 该出手那一日的止点时刻 */
    @TableField("due_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "该出手那一日的止点时刻")
    private Date dueAt;

    /** 可提前几日开口 */
    @TableField("amount")
    @ApiModelProperty(value = "可提前几日开口")
    private BigDecimal amount;

    /** 照办的事由文本 */
    @TableField("tpl_text")
    @ApiModelProperty(value = "照办的事由文本")
    private String tplText;

    /** 联系人手机（短信那一路）；空着即开不成单 */
    @TableField("contact_phone")
    @ApiModelProperty(value = "联系人手机（短信那一路）；空着即开不成单")
    private String contactPhone;

    /** 事由与所对名录项目或传承人记要 */
    @TableField("content")
    @ApiModelProperty(value = "事由与所对名录项目或传承人记要")
    private String content;

    /** 条目情形 0候办 1已办妥 2催不动 */
    @TableField("status")
    @ApiModelProperty(value = "条目情形 0候办 1已办妥 2催不动")
    private Integer status;

    /** 办妥的那一刻（与去向同一次落库，只落其一不算完事） */
    @TableField("done_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "办妥的那一刻（与去向同一次落库，只落其一不算完事）")
    private Date doneAt;

    /** 站内那一路送到时刻（县文旅局收） */
    @TableField("site_sent_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "站内那一路送到时刻（县文旅局收）")
    private Date siteSentAt;

    /** 手机那一路送到时刻（传承人本人收，不替站内交差） */
    @TableField("sms_sent_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "手机那一路送到时刻（传承人本人收，不替站内交差）")
    private Date smsSentAt;

    /** 催不动卡在哪一处缘由（按缘由挑得出） */
    @TableField("fail_reason")
    @ApiModelProperty(value = "催不动卡在哪一处缘由（按缘由挑得出）")
    private String failReason;

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

    public Integer getBizType() {
        return bizType;
    }

    public void setBizType(Integer bizType) {
        this.bizType = bizType;
    }

    public Integer getTargetType() {
        return targetType;
    }

    public void setTargetType(Integer targetType) {
        this.targetType = targetType;
    }

    public String getTargetNo() {
        return targetNo;
    }

    public void setTargetNo(String targetNo) {
        this.targetNo = targetNo;
    }

    public String getTargetName() {
        return targetName;
    }

    public void setTargetName(String targetName) {
        this.targetName = targetName;
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

    public String getTplText() {
        return tplText;
    }

    public void setTplText(String tplText) {
        this.tplText = tplText;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
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

    public Date getDoneAt() {
        return doneAt;
    }

    public void setDoneAt(Date doneAt) {
        this.doneAt = doneAt;
    }

    public Date getSiteSentAt() {
        return siteSentAt;
    }

    public void setSiteSentAt(Date siteSentAt) {
        this.siteSentAt = siteSentAt;
    }

    public Date getSmsSentAt() {
        return smsSentAt;
    }

    public void setSmsSentAt(Date smsSentAt) {
        this.smsSentAt = smsSentAt;
    }

    public String getFailReason() {
        return failReason;
    }

    public void setFailReason(String failReason) {
        this.failReason = failReason;
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
