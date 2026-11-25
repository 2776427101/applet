package com.banxing.classassistant.dto;
import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;
/**
 * 学生列表返回结构
 * 不分页，但要带 total：前端标题要显示「47 人在读」，
 * 有 total 就不用再单独请求一次 count
 */
@Data
@AllArgsConstructor
public class StudentListVO {
    private int total;
    private List<StudentVO> list;
}
