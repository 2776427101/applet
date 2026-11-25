package com.banxing.classassistant.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发送消息入参。
 *
 * <p>发送者永远是当前登录用户（从 token 取），前端夹带 {@code senderId} 会被 Jackson 丢弃。
 *
 * <p>{@code content} 上<b>没有 {@code @NotBlank}</b>：图片/视频/文件消息的正文是空的，
 * 加了这个注解媒体消息会被校验器直接拦下。正文是否必填由 Service 按 type 分支判断 ——
 * 校验注解是声明，条件必填只能写在业务里。
 */
@Data
public class MessageSendDTO {

    @Size(max = 500, message = "单条消息最多 500 字")
    private String content;

    /** 1=文本（默认） 2=图片 3=视频 4=文件 */
    private Integer type;

    /**
     * 媒体文件的相对路径，必须是上传接口返回的 {@code /files/...}。
     * 服务端会再校验一次前缀 —— 不校验的话谁都能往群里塞一个外部链接，
     * 变成"在群聊里挂一张可追踪的图"。
     */
    private String mediaUrl;

    @Size(max = 128, message = "文件名太长")
    private String fileName;

    private Long fileSize;

    /** 视频时长（秒） */
    private Integer duration;
}
