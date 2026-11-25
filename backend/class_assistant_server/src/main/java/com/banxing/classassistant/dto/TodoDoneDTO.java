package com.banxing.classassistant.dto;

import lombok.Data;

/**
 * 完成 / 取消完成（入参）
 */
@Data
public class TodoDoneDTO {

    // 1=标记完成 0=取消完成，别的值一律 1001
    private Integer done;
}