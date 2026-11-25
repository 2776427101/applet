package com.banxing.classassistant.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 成员列表项。
 *
 * <p>{@code displayName} 是后端算好的展示名（群昵称优先，其次真实姓名，最后「未设置姓名」）。
 * 前端不要自己再拼一遍 —— 两处拼法不一致时，列表和详情会显示成两个人。
 */
@Data
public class MemberVO {

    private Long userId;

    /** t_user.name，可能为空串（登录时自动建的账号没填名字） */
    private String name;

    private String avatar;

    private String nickname;

    /** 展示名：nickname > name > 「未设置姓名」 */
    private String displayName;

    /** 1=群主 2=管理员(班委) 3=成员 */
    private Integer role;

    /** 1=在群 2=被移除 3=已退出 */
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime joinedAt;

    /** 当前是否处于禁言中 */
    private Boolean muted;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime muteUntil;

    private String muteReason;

    /** 这一行是不是当前登录用户自己 */
    private Boolean self;
}
