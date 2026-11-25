package com.banxing.classassistant.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 学生端「我的成绩」
 *
 * <p>老师端是「一次考试 × 全班学生」的一张表（{@link ScoreListVO}），
 * 学生端必须转置成「一个学生 × 历次考试」的一列卡片。
 * 数据结构不能复用：老师要横着比（谁高谁低），学生要竖着看（我这次比上次好吗）。
 *
 * <p><b>排名由后端算。</b>前端要算就得知道全班每个人的总分，
 * 那等于把全班成绩发给每个学生 —— 这既是隐私问题，也让"只看自己"变成一句空话。
 */
@Data
public class StudentScoreVO {

    /** 按考试日期倒序，最近一次在最上面 */
    private List<ExamScore> list;

    private Summary summary;

    @Data
    public static class ExamScore {
        private Long examId;
        private String name;
        private Integer type;

        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate examDate;

        /** 表头顺序 */
        private List<String> subjects;

        /** 各科满分，键是科目名 */
        private Map<String, BigDecimal> fullScore;

        /** 我的分数，缺考为 null（不是 0） */
        private Map<String, BigDecimal> scores;

        /** 已录科目的总分，null 的科目不计入 */
        private BigDecimal total;

        /** 各科满分之和 */
        private BigDecimal fullTotal;

        /** 班级排名，同分并列（1、1、3）；没成绩是 null */
        private Integer rank;

        /** 参与排名的人数（有成绩的人） */
        private Integer classSize;

        /** 班级平均总分，用来判断"我这次在什么位置" */
        private BigDecimal classAvg;
    }

    @Data
    public static class Summary {
        /** 出成绩的考试次数 */
        private int examCount;
        /** 最好的一次排名，没成绩是 null */
        private Integer bestRank;
    }
}
