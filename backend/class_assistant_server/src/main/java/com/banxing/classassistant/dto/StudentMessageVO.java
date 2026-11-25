package com.banxing.classassistant.dto;

import lombok.Data;

/**
 * 学生端群聊里的一条消息（出参）
 *
 * <p>没有 senderId：前端判断"是不是我发的"用 {@link #mine}。两套自增 id 里
 * 都有 121 号，把 id 暴露出去只会诱导前端去比，比错就是"我的消息显示在别人那边"。
 *
 * <p>比老师端的 {@code MessageVO} 少了头像、群角色、接龙/抽签卡片的实时状态 ——
 * 学生端不显示头像，也没有"群主/班委"这些身份，卡片只降级显示 content 里的占位文案。
 * 多的 type / mediaUrl / fileName 是必须的：群里有图片和文件，只回 content 的话，
 * 学生在聊天页会看到一排空气泡（图片消息的 content 是空串）。
 */
@Data
public class StudentMessageVO {

    private Long id;

    /** 1=老师 2=学生。前端用它决定要不要显示名字、要不要打「老师」标签 */
    private Integer senderType;

    /** 发送者名字（学生消息是发送那一刻的快照） */
    private String senderName;

    private String content;

    /** 1=文本 2=图片 3=视频 4=文件 5=接龙卡片 6=抽签卡片 */
    private Integer type;

    /** 媒体相对路径，前端拼域名；文本消息为 null */
    private String mediaUrl;

    private String fileName;

    private Long fileSize;

    private Integer duration;

    /** 已格式化的时间，如 11-24 09:12 */
    private String time;

    /**
     * 原始时间 {@code yyyy-MM-dd HH:mm:ss}。
     *
     * <p>{@link #time} 只有「月-日 时:分」，做不了"相邻两条间隔超过 5 分钟才插一条时间"
     * 这个判断 —— 跨天、跨年时按字符串比大小也会错。老师端的 MessageVO 有 createdAt，
     * 两端的时间分隔规则必须一致，否则同一个群在两端看起来节奏不同。
     */
    private String createdAt;

    /** 是不是当前学生自己发的 */
    private Boolean mine;
}
