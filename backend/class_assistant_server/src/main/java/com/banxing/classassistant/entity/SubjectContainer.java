package com.banxing.classassistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学科班级容器，对应表 t_subject_container。
 *
 * <p>和 {@link ClassInfo}（行政班）是两个维度：行政班是"人在哪个班"，
 * 容器是"谁在上这门课"。一个行政班可以有多个学科容器，
 * 一个容器也能挂到行政班上（{@code classId}），但允许为 null（跨班选修）。
 *
 * <p>{@code joinPasswordHash} 绝不会出现在任何 VO 里 ——
 * 实体不直接返回给前端，是本项目所有接口的硬约定（见 ClassInfo 的处理方式）。
 */
@Data
@TableName("t_subject_container")
public class SubjectContainer {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 学科名称，必填，如「数学」 */
    private String subjectName;

    /** 容器显示名，默认「学科名 · 班级名」 */
    private String name;

    /** 关联行政班 t_class.id，可空 */
    private Long classId;

    /** 群主（负责老师）t_user.id */
    private Long ownerId;

    /** 10 位邀请码，全局唯一 */
    private String inviteCode;

    /** 邀请码截止时间，null = 永久有效 */
    private LocalDateTime inviteExpireAt;

    /** 加入密码哈希，null = 无密码 */
    private String joinPasswordHash;

    /** 密码最近设置时间，仅展示 */
    private LocalDateTime joinPasswordSetAt;

    /** 预留：聊天会话 ID，本期不做 IM */
    private Long sessionId;

    /** 1=正常 0=已解散 */
    private Integer status;

    private LocalDateTime dissolvedAt;

    private Long dissolvedBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
