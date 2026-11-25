package com.banxing.classassistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 学生用邀请码加入群聊
 *
 * <p>只有邀请码一个字段：学号和班级从 token 取。让前端传 studentId 的话，
 * 学生可以拿别人的学号去试某个群的码 —— 虽然还是要过花名册校验，但没必要留这个口子。
 *
 * <p>和 {@link StudentJoinDTO}（加入班级）是两个动作：那个换的是 token 里的 classId，
 * 这个只是在 {@code t_student_container} 里记一笔"我能看这个群"。
 */
@Data
public class StudentGroupJoinDTO {

    @NotBlank(message = "请填邀请码")
    @Size(max = 16, message = "邀请码不对")
    private String inviteCode;
}
