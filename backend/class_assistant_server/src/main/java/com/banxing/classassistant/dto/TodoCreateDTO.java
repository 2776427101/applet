package com.banxing.classassistant.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 新建待办（入参）
 * 只有两个字段，前端能传的就这么多
 */
@Data
public class TodoCreateDTO {

    // 内容，1~128 字
    private String title;

    // 截止时间，可不传
    // 入参方向同样要 @JsonFormat，否则 "2026-11-01 18:00:00" 解析不了
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime dueAt;
}