package com.banxing.classassistant.dto;

import com.banxing.classassistant.entity.LeaveRequest;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 一条请假记录（出参）
 * 比实体多一个 studentName：前端不该为了显示个名字再查一次学生表
 */
@Data
public class LeaveVO {

    private Long id;

    private Long studentId;

    // JOIN 出来的学生姓名（这里不是 JOIN，是批量查出来填进去的）
    private String studentName;

    // 1=病假 2=事假 3=其他
    private Integer type;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endAt;

    private String reason;

    // 0=待审批 1=已通过 2=已驳回 3=已销假
    private Integer status;

    // 审批备注：老师通过/驳回时填的话，可为 null
    private String auditRemark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /**
     * 实体转 VO。
     * studentName 由 Service 查好传进来，VO 自己不去查库
     */
    public static LeaveVO from(LeaveRequest r, String studentName) {
        LeaveVO vo = new LeaveVO();
        vo.setId(r.getId());
        vo.setStudentId(r.getStudentId());
        vo.setStudentName(studentName);
        vo.setType(r.getType());
        vo.setStartAt(r.getStartAt());
        vo.setEndAt(r.getEndAt());
        vo.setReason(r.getReason());
        vo.setStatus(r.getStatus());
        vo.setAuditRemark(r.getAuditRemark());
        vo.setCreatedAt(r.getCreatedAt());
        return vo;
    }
}
