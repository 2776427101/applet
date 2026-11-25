package com.banxing.classassistant.dto;

import lombok.Data;

import java.util.List;

/**
 * 抽签卡片（嵌在聊天消息里）。
 *
 * <p>只带"最新一轮"的结果，不带全部历史：
 * 一条消息每次轮询都要重新算一次卡片，把 20 轮 × 5 个名字全塞进去，
 * 群里只要多几张抽签卡，一次轮询就是几十 KB。
 *
 * <p>分组模式只给 {@code groupSummary}（如「4 组 · 每组约 14 人」），
 * 具体名单进结果页看。
 */
@Data
public class LotteryCardVO {

    private Long id;

    /** 1随机点名 2随机分组 */
    private Integer kind;

    /** 1未开始 2已开奖 3已取消 */
    private Integer status;

    /** 候选名单人数 */
    private Integer poolSize;

    private Integer drawCount;

    private Integer groupCount;

    /** 最新一轮中签的姓名（点名模式） */
    private List<String> latestNames;

    /** 分组摘要，如「4 组 · 每组约 14 人」；点名模式为空串 */
    private String groupSummary;

    /** 我是不是发起人。只有发起人能点「开始抽」 */
    private Boolean mine;
}
