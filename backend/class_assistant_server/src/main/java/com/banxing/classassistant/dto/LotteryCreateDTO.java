package com.banxing.classassistant.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.util.List;

/**
 * 发起抽签的入参。
 *
 * <p>没有"中奖概率"和"内定名单"这两个字段，是有意为之：
 * 抽签要的是"每个人机会一样"。一旦能设权重，它就从抽签变成了
 * 一个可以定向的工具，被学生发现就是信任问题。真要做，
 * 应该做成规则（"优先抽最近没被点到的"）而不是手动权重 —— 规则可解释。
 *
 * <p>{@code studentIds} 只在 {@code source=2}（手动勾选）时有意义。
 * 传了就按传的来，没传就取当前班全部在读学生。
 */
@Data
public class LotteryCreateDTO {

    /** 1随机点名 2随机分组，默认 1 */
    private Integer kind;

    /** 1当前班学生 2手动勾选，默认 1 */
    private Integer source;

    /** 手动勾选的学生 id 列表 */
    private List<Long> studentIds;

    /** 点名模式每次抽几人 */
    @Min(value = 1, message = "至少抽 1 人")
    @Max(value = 50, message = "一次最多抽 50 人")
    private Integer drawCount;

    /** 分组模式分几组 */
    @Min(value = 2, message = "至少分 2 组")
    @Max(value = 20, message = "最多分 20 组")
    private Integer groupCount;

    /** 已抽中的人不再进入下一轮，默认 true */
    private Boolean dedupe;

    /** 结果是否群内公开，默认 true */
    private Boolean publicResult;
}
