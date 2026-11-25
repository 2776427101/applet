package com.banxing.classassistant.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 容器详情。
 *
 * <p>{@code permissions} 只用于前端按钮显隐，<b>不是安全边界</b>：
 * 每个写接口在 Service 里都会再校验一次角色。
 * 之所以还要返回它，是因为「按钮灰着但点了报错」和「按钮根本不出现」
 * 对老师的体验差别很大。
 */
@Data
public class ContainerDetailVO {

    private Long id;

    private String name;

    private String subjectName;

    private Long classId;

    private String className;

    private Long ownerId;

    private String ownerName;

    /** 我在这个容器里的角色 */
    private Integer myRole;

    private Integer memberCount;

    /**
     * 本班学生人数（自动在群里，不占 t_container_member 的行）。
     *
     * <p>学生不是 t_user，进不了成员表，所以 {@code memberCount} 里没有他们。
     * 但他们在群里说话 —— 老师看到「1 人」会以为群是空的，所以单独给一个数字。
     * 只有绑定了班级的群才有值，公共群（如高数群）恒为 0。
     */
    private Integer studentCount;

    /** 管理员（班委）人数 */
    private Integer adminCount;

    private Boolean hasPassword;

    /** 仅群主/班委可见 */
    private String inviteCode;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime inviteExpireAt;

    private Boolean inviteExpired;

    /** 我当前是否被禁言 */
    private Boolean myMuted;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime myMuteUntil;

    /** 预留：聊天会话 ID，本期恒为 null */
    private Long sessionId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    private Permissions permissions;

    /** 当前角色能做什么。前端只读，后端不读 */
    @Data
    public static class Permissions {

        /** 改容器名称/学科/关联班级 */
        private boolean canEdit;
        /** 设置/取消管理员 */
        private boolean canManageAdmin;
        /** 移除成员（只有群主） */
        private boolean canRemoveMember;
        /** 禁言/解禁（群主 + 班委） */
        private boolean canMute;
        /** 收录/取消精华（群主 + 班委） */
        private boolean canHighlight;
        /** 查看邀请码 */
        private boolean canViewInvite;
        /** 重新生成邀请码 */
        private boolean canRegenerateCode;
        /** 设置/关闭加入密码 */
        private boolean canSetPassword;
        /** 解散容器 */
        private boolean canDissolve;
        /** 查看黑名单 */
        private boolean canViewBlacklist;
    }
}
