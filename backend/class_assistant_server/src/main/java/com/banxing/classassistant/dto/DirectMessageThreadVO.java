package com.banxing.classassistant.dto;

import lombok.Data;

import java.util.List;

/**
 * 一个私信线程：标题 + 消息列表 + 未读数
 *
 * <p>老师和学生共用这个结构，只是 title 不一样：
 * 老师看到的是学生名，学生看到的是"班主任姓名 + 班级"。
 */
@Data
public class DirectMessageThreadVO {

    /** 老师侧 = 学生姓名；学生侧 = 班主任姓名 */
    private String title;

    /** 副标题：老师侧是学号，学生侧是班级名 */
    private String subtitle;

    private List<DirectMessageVO> messages;

    /** 我这边还没读的条数 */
    private Integer unread;
}
