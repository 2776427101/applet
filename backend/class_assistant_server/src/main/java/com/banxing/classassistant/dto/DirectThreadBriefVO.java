package com.banxing.classassistant.dto;

import lombok.Data;

/**
 * 私信会话列表里的一行：最近聊过的某个学生。
 *
 * <p>只带列表要显示的东西（头像字、名字、最后一句、时间、未读红点），
 * 不把整个消息列表塞进来——列表页一次可能二十行，每行带一串消息没必要。
 */
@Data
public class DirectThreadBriefVO {

    /** 点进去要用的学生 id */
    private Long studentId;

    private String name;

    private String studentNo;

    /** 最后一条消息的内容，前端自己截断 */
    private String lastContent;

    /** 最后一条消息的时间，格式 MM-dd HH:mm */
    private String lastTime;

    /** 最后一条是谁发的：1=老师 2=学生。前端据此加"我："前缀 */
    private Integer lastSenderRole;

    /** 这个学生发来、我还没读的条数 */
    private Integer unread;
}
