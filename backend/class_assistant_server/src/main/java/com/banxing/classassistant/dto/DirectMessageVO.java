package com.banxing.classassistant.dto;

import lombok.Data;

/**
 * 一条私信
 */
@Data
public class DirectMessageVO {

    private Long id;

    /** 1=老师发的 2=学生发的。前端据此决定气泡靠左还是靠右 */
    private Integer senderRole;

    private String content;

    /** 已格式化好的时间，前端不再解析（小程序里 Date 解析 iOS 会踩坑） */
    private String time;

    /** 对方读没读。自己的消息显示"已读/未读"，对方的消息这个字段没意义 */
    private Boolean read;
}
