package com.banxing.classassistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 收录精华消息。
 *
 * <p>{@code messageId} 是给聊天上线后预留的：届时「长按某条消息 → 设为精华」
 * 只需要带上消息 id，{@code content} 仍由后端从消息表取快照写进来。
 * 本期没有聊天模块，{@code content} 就是班委手写的要点。
 */
@Data
public class HighlightCreateDTO {

    @NotBlank(message = "精华内容不能为空")
    @Size(max = 500, message = "精华内容最多 500 个字")
    private String content;

    /** 预留：聊天消息 ID，本期不传 */
    private Long messageId;
}
