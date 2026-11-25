package com.banxing.classassistant.dto;

import lombok.Data;

/**
 * 乐迪的回答
 */
@Data
public class AidiReplyVO {

    /** 给老师看的纯文本，可能带换行，前端直接用 text 渲染 */
    private String reply;

    /** 这一轮查了几次数据。前端不显示，日志和排查时有用 */
    private Integer toolCalls;

    /**
     * 需要老师确认的动作。null = 这轮只是问答，不动数据。
     * 非 null 时前端在气泡下面画一张确认卡，点了才调 /aidi/act。
     */
    private AidiActionVO action;
}
