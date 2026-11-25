package com.banxing.classassistant.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 学生发一条群消息
 *
 * <p>没有 classId / studentId / senderName：三个值都由后端从 token 和花名册取。
 * 让前端传 classId 就等于允许他往别的班发消息。
 *
 * <p>{@code content} 上<b>没有 {@code @NotBlank}</b>：图片/视频/文件消息的正文是空的，
 * 加了这个注解媒体消息会被校验器直接拦下。正文是否必填由 Service 按 type 分支判断 ——
 * 校验注解是声明，条件必填只能写在业务里。
 *
 * <p>字段和老师端的 {@link MessageSendDTO} 一一对应：两端发的是同一张表、同一批消息，
 * 一边能发图另一边不能，群里的气泡就会长成两种样子。
 */
@Data
public class StudentGroupSendDTO {

    /**
     * 上限 500，和老师端群聊对齐（{@code t_container_message.content} 是 varchar(500)）。
     * 原来写 1000 是因为那时消息存在自己的表里，现在是同一个群，
     * 学生能发 1000 字而老师只能发 500 字，会变成一条插不进去的溢出。
     */
    @Size(max = 500, message = "一条最多 500 字")
    private String content;

    /** 1=文本（默认） 2=图片 3=视频 4=文件 */
    private Integer type;

    /**
     * 媒体文件的相对路径，必须是上传接口返回的 {@code /files/...}。
     * 服务端会再校验一次前缀 —— 不校验的话谁都能往群里塞一个外部链接。
     */
    private String mediaUrl;

    @Size(max = 128, message = "文件名太长")
    private String fileName;

    private Long fileSize;

    /** 视频时长（秒） */
    private Integer duration;
}
