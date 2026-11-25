package com.banxing.classassistant.dto;

import lombok.Data;

import java.util.Map;

/**
 * 乐迪准备执行的一个动作（还没执行）
 *
 * <p><b>为什么要这一层</b>：模型只负责「提议」，真正落库要等老师在气泡里点确认。
 * 查错了只是看到个错数字，改错了是真的把数据改了 —— 审批请假、群发消息都没有撤销按钮。
 *
 * <p>{@code summary} 是给老师看的那一句（谁、什么、多少），必须把关键参数全摊开，
 * 不能写成「将执行审批操作」这种谁也不敢点的东西。
 */
@Data
public class AidiActionVO {

    /** 动作类型，和写工具同名，如 save_scores */
    private String type;

    /** 卡片标题，如「录入成绩」 */
    private String title;

    /** 一行摘要，把参数说清楚，如「黄旭铭 · 期中考试 · 语文 120 / 数学 135」 */
    private String summary;

    /** 确认按钮文案，如「确认录入」 */
    private String confirmText;

    /** 是否需要更醒目地提示（审批、群发这类不可撤销的） */
    private Boolean danger;

    /**
     * 回传给后端执行用的参数。
     *
     * <p>里面可能带 {@code _xxxId} 这种下划线开头的键，是预览阶段解析出来的 id。
     * 执行时会重新校验归属，所以前端改了也没用 —— 改不出这个班。
     */
    private Map<String, Object> params;
}
