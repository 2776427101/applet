package com.banxing.classassistant.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 接龙详情。
 *
 * <p>继承 {@link RelayCardVO} 而不是重新写一遍：卡片和详情页显示的是同一条接龙，
 * 状态、人数、我参没参与这些字段两边都要用。各写一份的结果是
 * 改了卡片忘了改详情，两处人数对不上。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RelayVO extends RelayCardVO {

    private String description;

    /** 单选格式的选项；其他格式为空数组 */
    private List<String> options;

    /** 数字格式的最小值 */
    private Integer formatMin;

    /** 数字格式的最大值 */
    private Integer formatMax;

    /** 参与者截止前能否改自己的条目 */
    private Boolean allowSelfEdit;

    /** 我填写的内容，没参与时为空串 */
    private String myValue;

    /** 参与名单，按 seq 升序 */
    private List<RelayEntryVO> entries;

    /**
     * 代填候选名单：当前班的学生。课程群没有关联班级时为空数组。
     * 前端不用再单独请求一次学生列表 —— 多一次请求就多一个失败点。
     */
    private List<CandidateVO> candidates;

    /** 代填候选人 */
    @Data
    public static class CandidateVO {
        private Long studentId;
        private String name;
        /** 已经在名单里（用于把已填的人置灰） */
        private Boolean picked;
    }
}
