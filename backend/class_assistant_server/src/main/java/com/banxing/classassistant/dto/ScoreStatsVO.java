package com.banxing.classassistant.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 成绩统计
 */
@Data
public class ScoreStatsVO {

    private Long examId;

    /**
     * 本班平均总分在同年级班级中的名次。
     * <b>算不出来时是 null，不是 1。</b>
     * 只有一个班时排第 1 是假象，老师会误以为「我们年级第一」。
     */
    private Integer gradeRank;

    // 同年级参与统计的班级数
    private Integer gradeClassCount;

    private List<SubjectStat> subjects;

    // 总分的三个统计值
    private BigDecimal totalAvg;
    private BigDecimal totalMax;
    private BigDecimal totalMin;

    /**
     * 单科统计
     */
    @Data
    public static class SubjectStat {
        private String subject;
        private BigDecimal avg;
        private BigDecimal max;
        private BigDecimal min;

        // 0~1 的小数（0.89 表示 89%），前端 ×100 显示。
        // 用小数不用百分数，是因为小数能直接参与计算和画图
        private BigDecimal passRate;

        private BigDecimal fullScore;
    }
}
