package com.banxing.classassistant.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 加入成功的返回。前端拿到 containerId 直接 redirectTo 详情页。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JoinResultVO {

    private Long containerId;

    private String name;

    /** 加入后的角色，正常都是 3（成员） */
    private Integer role;
}
