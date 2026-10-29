package com.banxing.classassistant.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 考试（列表里的一项）
 */
@Data
public class ExamVO {

    private Long id;

    private String name;

    // 1=周测 2=月考 3=期中 4=期末
    private Integer type;

    // 出参方向也要 @JsonFormat，否则会序列化成 "2026-10-20T00:00:00.000+08:00"
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate examDate;

    // 数据库里存的是 "语文,数学,英语"，这里转成数组给前端。
    // 前端表头按这个数组动态生成，加科目不用改前端代码
    private List<String> subjects;

    // 已录入的成绩条数，0 表示还没录
    private Long scoreCount;
}
