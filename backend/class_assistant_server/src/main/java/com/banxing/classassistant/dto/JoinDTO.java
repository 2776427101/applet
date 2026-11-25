package com.banxing.classassistant.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 用邀请码加入容器。
 *
 * <p>{@code code} 在 Service 里会先 normalize（去空格 + 转大写）再校验格式，
 * 所以前端不用替用户清洗输入。
 * <p>{@code password} 只在容器设了密码时必填，且<b>不会出现在任何日志里</b>。
 */
@Data
public class JoinDTO {

    @NotBlank(message = "请输入邀请码")
    private String code;

    private String password;
}
