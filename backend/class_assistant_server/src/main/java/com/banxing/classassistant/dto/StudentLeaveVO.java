package com.banxing.classassistant.dto;

import com.banxing.classassistant.entity.LeaveRequest;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 一条请假记录（学生端出参）
 *
 * <p>和老师端 {@link LeaveVO} 的差别只有三处，但每一处都是"权限"：
 * <ul>
 *   <li>没有 studentName —— 学生只看自己的，写自己名字是废话</li>
 *   <li>没有 studentId —— 前端拿不到别人的 id，也就拼不出越权请求</li>
 *   <li>多两个布尔 canCancel / canReport —— <b>能不能撤销由后端说了算</b>。
 *       前端如果自己写 {@code status === 0} 判断，规则一改两边就不同步，
 *       而且这种不同步只会在"按钮能点但点了报错"时才被发现</li>
 * </ul>
 */
@Data
public class StudentLeaveVO {

    private Long id;

    /** 1=病假 2=事假 3=其他 */
    private Integer type;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endAt;

    private String reason;

    /** 0=待审批 1=已通过 2=已驳回 3=已销假 */
    private Integer status;

    /** 班主任审批时填的话。驳回时通常有，通过时常常是空的 */
    private String auditRemark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    /** 还能不能撤销：只有"待审批"可以 */
    private boolean canCancel;

    /** 能不能销假：只有"已通过"可以 */
    private boolean canReport;

    public static StudentLeaveVO from(LeaveRequest r) {
        StudentLeaveVO vo = new StudentLeaveVO();
        vo.setId(r.getId());
        vo.setType(r.getType());
        vo.setStartAt(r.getStartAt());
        vo.setEndAt(r.getEndAt());
        vo.setReason(r.getReason());
        vo.setStatus(r.getStatus());
        vo.setAuditRemark(r.getAuditRemark());
        vo.setCreatedAt(r.getCreatedAt());
        vo.setUpdatedAt(r.getUpdatedAt());
        int st = r.getStatus() == null ? -1 : r.getStatus();
        vo.setCanCancel(st == 0);
        vo.setCanReport(st == 1);
        return vo;
    }
}
