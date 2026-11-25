package com.banxing.classassistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 加入密码错误计数，对应表 t_container_join_attempt。
 *
 * <p>落库而不是放内存：重启清零、多实例各算各的，等于没有限制。
 * 按 (containerId, userId) 计，唯一键保证并发下只有一行。
 */
@Data
@TableName("t_container_join_attempt")
public class ContainerJoinAttempt {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long containerId;

    private Long userId;

    private Integer failCount;

    /** null = 未锁定 */
    private LocalDateTime lockedUntil;

    private LocalDateTime updatedAt;
}
