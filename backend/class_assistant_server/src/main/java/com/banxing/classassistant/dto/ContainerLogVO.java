package com.banxing.classassistant.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 容器操作日志项。
 */
@Data
public class ContainerLogVO {

    /** SET_ADMIN / MUTE / REMOVE / DISSOLVE ... 中文文案由前端映射 */
    private String actionType;

    private String detail;

    private Long operatorId;

    private String operatorName;

    private Long targetUserId;

    private String targetName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
