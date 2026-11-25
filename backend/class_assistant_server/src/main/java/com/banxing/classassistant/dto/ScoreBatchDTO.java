package com.banxing.classassistant.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 批量录入成绩的入参。
 *
 * <p>一次提交整张表。47 个学生如果一人一个请求就是 47 次往返，
 * 小程序上会明显卡顿，而且没法做"全成功或全回滚"。
 */
@Data
public class ScoreBatchDTO {

    private Long examId;

    private List<Row> rows;

    @Data
    public static class Row {
        private Long studentId;

        // 键必须是本次考试的 subjects 里的科目；值 null 表示缺考
        private Map<String, BigDecimal> scores;
    }
}
