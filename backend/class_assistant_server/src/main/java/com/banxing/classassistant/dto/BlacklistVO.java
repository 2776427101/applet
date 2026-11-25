package com.banxing.classassistant.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 黑名单列表项（只有群主能拿到）。
 */
@Data
public class BlacklistVO {

    private Long userId;

    private String name;

    private String displayName;

    private String reason;

    /** 拉黑操作人姓名 */
    private String operatorName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
