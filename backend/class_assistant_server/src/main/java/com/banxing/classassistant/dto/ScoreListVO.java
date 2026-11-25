package com.banxing.classassistant.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 某次考试的成绩表
 */
@Data
public class ScoreListVO {

    private ExamDetail exam;

    private List<ScoreRow> list;

    /**
     * 表头信息：前端靠它决定画几列、每列满分多少
     */
    @Data
    public static class ExamDetail {
        private Long id;
        private String name;
        private Integer type;

        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate examDate;

        private List<String> subjects;

        // 各科满分，键是科目名
        private Map<String, BigDecimal> fullScore;
    }

    /**
     * 一个学生一行
     */
    @Data
    public static class ScoreRow {
        private Long studentId;
        private String studentName;
        private String studentNo;

        // 键是科目名，值是分数。缺考为 null（不是 0）
        //
        // 为什么用对象而不是数组 [{subject, score}]：
        // 前端可以直接 scores['语文'] 取值，表头顺序变了也不用改代码
        private Map<String, BigDecimal> scores;

        // 已录科目的总分，null 的科目不计入
        private BigDecimal total;

        // 按 total 降序，同分并列（1、1、3）。后端算，前端不算
        private Integer rank;
    }
}
