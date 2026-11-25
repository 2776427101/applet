package com.banxing.classassistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 请假记录实体，对应表 t_leave_request
 */
@Data
@TableName("t_leave_request")
public class LeaveRequest {

    // 主键，自增
    @TableId(type = IdType.AUTO)
    private Long id;

    // 班级 ID（冗余字段，统计时不用再 join 学生表）
    private Long classId;

    // 请假的学生 ID
    private Long studentId;

    // 请假类型：1=病假 2=事假 3=其他
    private Integer type;

    // 请假开始时间
    private LocalDateTime startAt;

    // 请假结束时间
    private LocalDateTime endAt;

    // 请假原因
    private String reason;

    // 状态：0=待审批 1=已通过 2=已驳回 3=已销假
    private Integer status;

    // 审批备注：老师通过/驳回时填的话，可为 null
    // 契约 §9.2 的 remark 字段存在这里
    private String auditRemark;

    // 创建时间
    private LocalDateTime createdAt;

    // 更新时间
    private LocalDateTime updatedAt;
}
