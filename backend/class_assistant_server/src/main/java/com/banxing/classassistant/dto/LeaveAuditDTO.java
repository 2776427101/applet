package com.banxing.classassistant.dto;

import lombok.Data;

/**
 * 审批请假（入参）
 */
@Data
public class LeaveAuditDTO {

    // 只能传 1（通过）或 2（驳回），传别的返回 1001
    private Integer status;

    // 审批备注，≤255 字，可不传
    private String remark;
}
