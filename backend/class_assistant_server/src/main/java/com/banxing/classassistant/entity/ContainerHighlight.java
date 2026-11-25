package com.banxing.classassistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 容器精华消息，对应表 t_container_highlight。
 *
 * <p>本期没有聊天模块，{@code messageId} 恒为 null，{@code content} 是班委/老师手写的要点快照。
 * 聊天上线后 {@code messageId} 才被填上，届时两种记录可以共存，
 * 不需要为"从消息收录"另建一张表。
 */
@Data
@TableName("t_container_highlight")
public class ContainerHighlight {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long containerId;

    /** 预留：聊天消息 ID，本期为 null */
    private Long messageId;

    /** 精华内容（消息快照或手写要点） */
    private String content;

    private Long creatorId;

    /** 1=有效 0=已取消 */
    private Integer status;

    private LocalDateTime createdAt;

    private Long canceledBy;

    private LocalDateTime canceledAt;
}
