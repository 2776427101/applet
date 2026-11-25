package com.banxing.classassistant.dto;

import lombok.Data;

/**
 * 学生端看到的一个群（出参），通讯录的群卡片和聊天页顶部都用它
 *
 * <p>{@code id} 是 {@code t_subject_container.id}，不是 classId —— 老师端的群
 * 本来就是"学科容器"，一个班可能有好几个（班级群、课程群），用一个 classId 表示不了。
 */
@Data
public class StudentGroupVO {

    private Long id;

    /** 群名，老师建群时填的 */
    private String name;

    /** 学科名，如「高等数学」。班级群这一栏通常和 name 一样 */
    private String subjectName;

    /**
     * 人数：群里的老师 + 本班学生 + 用邀请码加进来的其他学生。
     *
     * <p>老师端那个 memberCount 只算 t_container_member（老师），
     * 直接拿来显示会变成"1 人的群里有 57 个人说话"。
     */
    private int memberCount;

    /** 最后一条消息的摘要，没有消息时为 null */
    private String lastContent;

    /** 最后一条消息的时间，如 11-24 09:12 */
    private String lastTime;

    /**
     * true = 本班的群（花名册里有我就自动可见，不需要邀请码）；
     * false = 用邀请码加入的其他群。
     *
     * <p>前端拿它决定卡片上写「本班群」还是「已加入」。
     */
    private boolean classGroup;

    /** 关联的行政班名。公共群（高数群这种）为 null，群信息页就不显示这一行 */
    private String className;

    /** 群主姓名。学生看群信息时需要知道"这个群是谁的" */
    private String ownerName;

    /** 群里老师人数（不含学生）。群信息页写「老师 1 人 · 学生 58 人」 */
    private int teacherCount;

    /** 群里学生人数（本班 + 用码加进来的） */
    private int studentCount;

    /**
     * 能不能退出这个群。
     *
     * <p>本班的群不能退：学生的"在群里"是由 class_id 派生的，退不掉 ——
     * 给一个点了没反应的按钮比不给更糟。只有用邀请码加进来的群能退。
     */
    private boolean canLeave;
}
