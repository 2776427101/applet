package com.banxing.classassistant.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 邀请码信息（只有群主和班委能拿到）。
 *
 * <p>返回值里没有密码，只有 {@code hasPassword} 这个布尔 ——
 * 密码哈希永远不出后端。
 */
@Data
public class InviteVO {

    private Long containerId;

    private String inviteCode;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime inviteExpireAt;

    /** 已过期。永久有效时为 false */
    private Boolean inviteExpired;

    /** 是否设置了加入密码 */
    private Boolean hasPassword;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime joinPasswordSetAt;
}
