package com.banxing.classassistant.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 新建考试的入参。
 *
 * <p>注意这里只有 5 个字段 —— 字段列表就是前端的权限边界。
 * 千万不能写成 {@code @RequestBody Exam}：那样前端可以自己传 classId，
 * 就能往别人的班里塞考试。
 */
@Data
public class ExamCreateDTO {

    private String name;

    // 不传默认 2（月考）
    private Integer type;

    // 入参方向必须写 @JsonFormat，
    // 否则前端传 "2026-10-20" 这种字符串会抛 InvalidFormatException，
    // 被兜底接成 5000，前端只能看到"服务器开小差了"
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate examDate;

    private List<String> subjects;

    // 各科满分，可选。不传就是全部 100。
    // 例：{"语文":150,"数学":150}
    private Map<String, Integer> fullScore;
}
