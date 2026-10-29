package com.banxing.classassistant.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 待办列表（出参）
 * total 和 list 分开给：以后加分页时前端不用改结构
 */
@Data
@AllArgsConstructor
public class TodoListVO {

    private int total;

    private List<TodoVO> list;
}