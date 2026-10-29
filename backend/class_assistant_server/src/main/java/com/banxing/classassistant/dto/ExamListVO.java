package com.banxing.classassistant.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 考试列表
 */
@Data
@AllArgsConstructor
public class ExamListVO {

    private long total;

    private int page;

    private int size;

    private List<ExamVO> list;
}
