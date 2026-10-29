package com.banxing.classassistant.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 代学生请假（入参）
 *
 * 注意没有 status 字段：老师代录的一律是"已通过"，
 * 这个值由后端写死，前端说了不算
 */
@Data
public class LeaveCreateDTO {

    // 请假的学生，必须在本班
    private Long studentId;

    // 1=病假 2=事假 3=其他
    private Integer type;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endAt;

    // 原因，≤255 字，可不传
    private String reason;
}
