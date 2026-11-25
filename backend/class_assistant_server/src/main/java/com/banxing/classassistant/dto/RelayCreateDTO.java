package com.banxing.classassistant.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 发起接龙的入参。
 *
 * <p>没有 {@code containerId} 和 {@code creatorId}：群 id 在路径上，
 * 发起人从 token 取。让前端传这两个等于把"替别人发一条接龙"变成合法操作。
 *
 * <p>{@code options} 只在 {@code entryFormat=5}（单选）时有意义，
 * 用 List 而不是逗号分隔的字符串 —— 选项文本里出现逗号是很正常的事
 * （"周一、周三"用的是顿号，但"上午,下午"就会炸）。
 */
@Data
public class RelayCreateDTO {

    @NotBlank(message = "接龙主题不能为空")
    @Size(max = 30, message = "主题最多 30 个字")
    private String title;

    @Size(max = 500, message = "说明最多 500 个字")
    private String description;

    /** 1序号 2数字 3手机号 4短文本 5单选，默认 1 */
    private Integer entryFormat;

    /** 格式=数字 时的最小值 */
    private Integer formatMin;

    /** 格式=数字 时的最大值 */
    private Integer formatMax;

    /** 格式=单选 时的选项 */
    private List<String> options;

    /** 人数上限，null = 不限 */
    private Integer maxCount;

    /** 截止时间，null = 不限 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime deadline;

    /** 参与者能否改自己的条目，默认 true */
    private Boolean allowSelfEdit;
}
