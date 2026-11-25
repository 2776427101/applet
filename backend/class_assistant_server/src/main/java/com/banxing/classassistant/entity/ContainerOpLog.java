package com.banxing.classassistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 容器敏感操作日志，对应表 t_container_op_log。
 *
 * <p>只回答「谁对谁做了什么」，不存业务快照 —— 查历史状态应该看业务表。
 * {@code detail} 由 Service 拼固定文案，<b>不透传用户入参</b>，
 * 避免有人把密码写进「备注」里然后被日志原样存下来。
 */
@Data
@TableName("t_container_op_log")
public class ContainerOpLog {

    public static final String SET_ADMIN = "SET_ADMIN";
    public static final String UNSET_ADMIN = "UNSET_ADMIN";
    public static final String MUTE = "MUTE";
    public static final String UNMUTE = "UNMUTE";
    public static final String REMOVE = "REMOVE";
    public static final String DISSOLVE = "DISSOLVE";
    public static final String UPDATE = "UPDATE";
    public static final String REGEN_CODE = "REGEN_CODE";
    public static final String SET_PASSWORD = "SET_PASSWORD";
    public static final String SET_EXPIRE = "SET_EXPIRE";
    public static final String BLACKLIST = "BLACKLIST";
    public static final String RELEASE_BLACKLIST = "RELEASE_BLACKLIST";
    public static final String HIGHLIGHT = "HIGHLIGHT";
    public static final String CANCEL_HIGHLIGHT = "CANCEL_HIGHLIGHT";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long containerId;

    private Long operatorId;

    private Long targetUserId;

    private String actionType;

    private String detail;

    private LocalDateTime createdAt;
}
