package com.banxing.classassistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 学生凭邀请码加入（切换）班级
 *
 * <p>只有邀请码一个字段：学号从 token 里的学生行取。
 * 让前端传学号的话，学生可以拿别人的学号去试某个班的码 ——
 * 虽然还是要过花名册校验，但没必要留这个口子。
 */
@Data
public class StudentJoinDTO {

    @NotBlank(message = "请填邀请码")
    @Size(max = 16, message = "邀请码不对")
    private String inviteCode;
}
