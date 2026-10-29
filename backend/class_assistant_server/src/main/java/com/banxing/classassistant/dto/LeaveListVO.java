package com.banxing.classassistant.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 请假列表（出参）
 * 比学生、待办多了 page / size：请假记录一个学期会累积几百条，必须分页
 */
@Data
@AllArgsConstructor
public class LeaveListVO {

    private long total;

    private int page;

    private int size;

    private List<LeaveVO> list;
}
