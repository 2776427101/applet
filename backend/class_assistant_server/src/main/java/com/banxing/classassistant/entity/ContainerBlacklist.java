package com.banxing.classassistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 容器黑名单，对应表 t_container_blacklist。
 *
 * <p>只有群主能看、能操作。被拉黑的人不能用邀请码加入，直到群主解除。
 * 解除不删行，只写 {@code releasedAt} —— 「谁在什么时候把谁放出来」要能查。
 */
@Data
@TableName("t_container_blacklist")
public class ContainerBlacklist {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long containerId;

    private Long userId;

    private String reason;

    private Long operatorId;

    private LocalDateTime createdAt;

    private Long releasedBy;

    /** null = 仍在黑名单 */
    private LocalDateTime releasedAt;

    public boolean isActive() {
        return releasedAt == null;
    }
}
