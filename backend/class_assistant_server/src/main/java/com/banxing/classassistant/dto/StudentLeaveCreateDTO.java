package com.banxing.classassistant.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学生提交请假（入参）
 *
 * <p>和老师端的 {@link LeaveCreateDTO} 比，<b>少了 studentId</b>。
 * 这不是省事，是安全：学生提交的请假只能是他自己的，
 * 让前端传 studentId 就等于允许他替全班人请假。
 * 学生 id 从 token 里取，前端根本不知道也不该知道怎么指定。
 */
@Data
public class StudentLeaveCreateDTO {

    /** 1=病假 2=事假 3=其他 */
    private Integer type;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endAt;

    /** ≤255 字，可不填 */
    private String reason;
}
