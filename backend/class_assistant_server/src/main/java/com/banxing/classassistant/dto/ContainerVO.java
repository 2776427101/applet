package com.banxing.classassistant.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 容器列表项。
 *
 * <p>{@code inviteCode} 对普通成员（role=3）返回 null：
 * 邀请码的查看权限是群主/班委，成员拿到码就能无限拉人进来。
 * 前端拿到 null 显示「—」，不显示「无」——「无」会被误解成这个容器没有码。
 */
@Data
public class ContainerVO {

    private Long id;

    private String name;

    private String subjectName;

    /** 关联的行政班名；未关联为 null */
    private String className;

    /** 我在这个容器里的角色：1=群主 2=管理员 3=成员 */
    private Integer myRole;

    private Integer memberCount;

    /**
     * 本班学生人数（自动在群里，不占 t_container_member 的行）。
     *
     * <p>学生不是 t_user，进不了成员表，所以 memberCount 里没有他们。
     * 列表里显示「1 人」而群里有 57 个学生在说话，老师只会以为数据错了。
     * 公共群（没绑班级）恒为 0。
     */
    private Integer studentCount;

    /** 仅群主/班委可见，其他角色为 null */
    private String inviteCode;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime inviteExpireAt;

    /** 邀请码是否已过期。永久有效时为 false */
    private Boolean inviteExpired;

    private Boolean hasPassword;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
