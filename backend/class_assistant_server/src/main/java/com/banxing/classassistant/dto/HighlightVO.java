package com.banxing.classassistant.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 精华消息列表项。
 */
@Data
public class HighlightVO {

    private Long id;

    private String content;

    private Long creatorId;

    private String creatorName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /** 是不是我收录的。班委只能取消自己收录的，前端靠它决定要不要显示「取消」 */
    private Boolean mine;

    /** 预留：来源消息 ID，本期恒为 null */
    private Long messageId;
}
