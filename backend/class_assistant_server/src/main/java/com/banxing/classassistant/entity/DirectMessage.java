package com.banxing.classassistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 师生私信，对应表 t_direct_message
 *
 * <p>一行 = 一条消息。线程靠 (classId, studentId) 圈定，不单独建会话表 ——
 * 一对一的会话没有"群名、成员、公告"这些属性，建表只会多一次 join。
 */
@Data
@TableName("t_direct_message")
public class DirectMessage {

    /** 老师发的 */
    public static final int FROM_TEACHER = 1;

    /** 学生发的 */
    public static final int FROM_STUDENT = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long classId;

    private Long studentId;

    private Long teacherId;

    /** 1=老师 2=学生，见 {@link #FROM_TEACHER} / {@link #FROM_STUDENT} */
    private Integer senderRole;

    private String content;

    /** 对方读的时间；null = 未读 */
    private LocalDateTime readAt;

    private LocalDateTime createdAt;
}
