package com.banxing.classassistant.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 成绩表导入的预览结果。
 *
 * <p>和学生名单导入（{@link ImportPreviewVO}）长得像，但有一处根本区别：
 * <b>成绩是按「格」算的，不是按「行」算的</b>。
 * 一行学生里有 6 科，可能语文是新增、数学覆盖旧分、英语被清成缺考。
 * 所以统计不能只说「能导 45 行」，必须说清楚每一类格子有多少个 ——
 * 否则老师点「确认」时根本不知道自己在改什么。
 */
@Data
public class ScoreImportPreviewVO {

    private Long examId;

    private String examName;

    private String sheetName;

    /** 表头在 Excel 第几行 */
    private Integer headerRowNo;

    /** 数据行数（不含表头、空行） */
    private Integer dataRows;

    private Integer blankRows;

    /** 本次考试的科目，按考试里配置的顺序 */
    private List<String> subjects;

    /** 各科满分，前端显示「语文 / 150」用 */
    private Map<String, BigDecimal> fullScore;

    /** 识别到的列，从左到右 */
    private List<ColumnVO> columns;

    /**
     * 表里有、但没认成任何科目的列名，如「总分」「班级排名」。
     *
     * <p>不报错。成绩表里常有总分、排名、等级这些派生列，
     * 它们是算出来的，导进来反而会和系统的统计打架。
     */
    private List<String> unmappedHeaders;

    /**
     * 考试里有、但表里没找到对应列的科目。
     *
     * <p>必须单独告诉老师：这次导入不会碰这些科目，
     * 而不是让他以为「我导完了，怎么英语还是空的」。
     */
    private List<String> missingSubjects;

    private StatsVO stats;

    private List<RowVO> rows;

    @Data
    public static class ColumnVO {
        private Integer column;

        /** Excel 里的原始表头文字 */
        private String header;

        /** 认成了哪个科目，没认出来是 null */
        private String subject;

        /** 认成了哪个身份字段（NAME / STUDENT_NO / CLASS_NAME），不是身份列就是 null */
        private String field;

        /** 身份字段的中文名，如「学号」 */
        private String fieldLabel;

        /** 这一列有没有用上。科目列和身份列都算用上了 */
        private Boolean mapped;
    }

    /**
     * 按「格」计数。
     *
     * <p>{@code cells = create + overwrite + clear}，三者互斥且覆盖全部会写库的格子。
     * 「库里本来就是空、表里也是空」的格子不计入任何一项 —— 它不产生任何写入。
     */
    @Data
    public static class StatsVO {
        /** 表里有几行数据 */
        private int rows;

        /** 能导入的行数（有错的行不算） */
        private int importable;

        /** 有错、导不进去的行数 */
        private int error;

        /** 会写入的总格数 */
        private int cells;

        /** 库里没有、这次新填的格数 */
        private int create;

        /** 库里有旧分、这次被改掉的格数 */
        private int overwrite;

        /**
         * 库里有旧分、这次被清成缺考的格数。
         *
         * <p><b>单独计数是因为它最危险</b>：Excel 里空着的一格，
         * 在「整张表就是这次考试的完整成绩」的假设下等于缺考。
         * 但如果老师只是拿一张只填了一半的表来补录，空着的地方本来是有分的，
         * 导进去就全没了。这个数字大于 0 时，前端必须红字提醒。
         */
        private int clear;
    }

    @Data
    public static class RowVO {
        private Integer excelRowNo;

        private String name;

        private String studentNo;

        /** 匹配到的学生 id，没匹配上是 null */
        private Long studentId;

        /** create（全是新增）/ overwrite（有旧分会被改）/ error */
        private String action;

        /**
         * 科目 → 分数。null 表示这一格是空的（缺考）。
         *
         * <p><b>不包含表里没有的科目</b>：这一点是安全边界。
         * 如果表里没有英语列，这里就不会有 "英语" 这个 key，
         * 提交时后端也就不会碰英语成绩。多塞一个 null 进去，
         * 会把全班英语分数清空 —— 那是无法撤销的。
         */
        private Map<String, BigDecimal> scores;

        private List<String> errors;

        /** 一句话原因，给列表显示用 */
        private String reason;
    }
}
