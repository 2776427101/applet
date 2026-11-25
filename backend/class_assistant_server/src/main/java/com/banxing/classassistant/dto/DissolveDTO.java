package com.banxing.classassistant.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 解散容器。
 *
 * <p>要求把容器名原样打一遍（{@code confirmName}），后端比对不一致就拒绝。
 * 解散是不可逆的（成员全部无法访问），一个「确定吗」的弹窗拦不住手滑，
 * 让老师把名字敲一遍才拦得住。
 */
@Data
public class DissolveDTO {

    @NotBlank(message = "请输入容器名称以确认解散")
    private String confirmName;
}
