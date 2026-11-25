package com.banxing.classassistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 容器成员，对应表 t_container_member。
 *
 * <p>角色常量放在这里而不是散在各 Service：
 * {@code ROLE_OWNER} 群主（老师）、{@code ROLE_ADMIN} 管理员（班委）、{@code ROLE_MEMBER} 普通成员。
 * 注意这套 role 和 {@link User#getRole()}（1=老师 2=学生）<b>完全无关</b>，
 * 别把两个 role 混着判断。
 */
@Data
@TableName("t_container_member")
public class ContainerMember {

    /** 群主（负责老师） */
    public static final int ROLE_OWNER = 1;
    /** 管理员（班委） */
    public static final int ROLE_ADMIN = 2;
    /** 普通成员 */
    public static final int ROLE_MEMBER = 3;

    /** 在群 */
    public static final int STATUS_IN = 1;
    /** 被移除 */
    public static final int STATUS_REMOVED = 2;
    /** 主动退出 */
    public static final int STATUS_LEFT = 3;

    /** 永久禁言的到期时间哨兵值：只用一个字段，避免「永久 + 有到期时间」的矛盾态 */
    public static final LocalDateTime MUTE_FOREVER = LocalDateTime.of(9999, 12, 31, 23, 59, 59);

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long containerId;

    private Long userId;

    /** 1=群主 2=管理员(班委) 3=成员 */
    private Integer role;

    /** 1=在群 2=被移除 3=已退出 */
    private Integer status;

    /** 群内昵称，空则用 t_user.name */
    private String nickname;

    /** 1=邀请码 2=创建者 3=管理员添加 */
    private Integer joinSource;

    private LocalDateTime joinedAt;

    /** 禁言到期；null=未禁言；9999-12-31 23:59:59=永久 */
    private LocalDateTime muteUntil;

    private String muteReason;

    private Long muteBy;

    private LocalDateTime muteAt;

    private Long removedBy;

    private LocalDateTime removedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /** 当前是否处于禁言中（含永久禁言）。到期时间已过的视为未禁言 */
    public boolean isMuted() {
        return muteUntil != null && muteUntil.isAfter(LocalDateTime.now());
    }
}
