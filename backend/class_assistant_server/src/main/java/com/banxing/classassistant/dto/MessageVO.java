package com.banxing.classassistant.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 一条聊天消息（出参）。
 *
 * <p>{@code mine} 由后端算好，前端不用拿自己的 userId 去比 ——
 * 小程序端没有全局用户态，让页面自己判断会多一次存储读取。
 */
@Data
public class MessageVO {

    private Long id;

    private Long senderId;

    /**
     * 发送者类型：1=老师 2=学生。
     *
     * <p>前端用它决定要不要打「学生」标签，也用它判断 {@code mine} ——
     * 两套 id 空间里都有 121 号，只比 id 会把别人的消息标成"我发的"。
     */
    private Integer senderType;

    /** 展示名：群昵称 > 真实姓名 > 未设置姓名 */
    private String senderName;

    private String senderAvatar;

    /** 发送者在群里的角色 1群主 2班委 3成员，前端给群主/班委加个小标签 */
    private Integer senderRole;

    private String content;

    /** 1=文本 2=图片 3=视频 4=文件 5=接龙卡片 6=抽签卡片 */
    private Integer type;

    /**
     * 卡片消息指向的业务 id。type=5 时是接龙 id，type=6 时是抽签 id。
     * 前端点卡片时拿它跳详情页，不用去解析 content。
     */
    private Long refId;

    /**
     * 接龙卡片的实时状态。type=5 时才有值。
     *
     * <p>由 {@code list()} 批量补齐，不是存在消息表里的 ——
     * 接龙人数一直在变，存进消息表就要有人去同步，同步漏一次卡片上的人数就永久错了。
     */
    private RelayCardVO relayCard;

    /** 抽签卡片的实时状态。type=6 时才有值 */
    private LotteryCardVO lotteryCard;

    /** 媒体文件的相对路径，前端拼域名后给 image/video 用；文本消息为 null */
    private String mediaUrl;

    /** 原始文件名，文件气泡上显示 */
    private String fileName;

    /** 字节数，前端格式化成 KB/MB */
    private Long fileSize;

    /** 视频时长（秒） */
    private Integer duration;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /** 是不是我发的 */
    private Boolean mine;
}
