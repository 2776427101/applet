package com.banxing.classassistant.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建容器的返回。只回 id 和邀请码 —— 老师创建完最需要的就是这两样。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContainerCreateVO {

    private Long id;

    private String inviteCode;
}
