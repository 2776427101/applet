package com.banxing.classassistant.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 容器列表响应。容器数量有天然上限（一个老师带几门课），所以不分页。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContainerListVO {

    private int total;

    private List<ContainerVO> list;
}
