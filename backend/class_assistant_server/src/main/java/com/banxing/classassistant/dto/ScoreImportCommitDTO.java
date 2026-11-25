package com.banxing.classassistant.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 确认导入成绩的入参。
 *
 * <p>rows 就是预览返回的那一份，原样回传。后端会重新校验 ——
 * 前端传来的 action / 统计一概不看，只信 studentId 和 scores。
 */
@Data
public class ScoreImportCommitDTO {

    private Long examId;

    private List<Row> rows;

    @Data
    public static class Row {
        private Integer excelRowNo;

        /** 学生 id。预览阶段已经匹配好了，提交时只认这个 */
        private Long studentId;

        /**
         * 科目 → 分数，null = 缺考。
         *
         * <p><b>没有 key 的科目表示「表里没这一列，不要动」</b>。
         * 后端靠这个区分「明确填了缺考」和「这次没导入这一科」。
         * 前端千万不要给缺席的科目补 null —— 那等于把全班这一科清空。
         */
        private Map<String, BigDecimal> scores;
    }
}
