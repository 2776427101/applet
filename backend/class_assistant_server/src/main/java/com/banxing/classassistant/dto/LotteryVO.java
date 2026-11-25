package com.banxing.classassistant.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 抽签结果页。
 *
 * <p>分组模式给完整的 {@code groups}；点名模式给 {@code rounds}（每轮一批）。
 * 两种模式的结果形状不一样，硬塞进一个字段前端要写两套判断，
 * 不如各给一个，用 kind 决定读哪个。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class LotteryVO extends LotteryCardVO {

    /** 去重模式下还剩多少人可抽 */
    private Integer remaining;

    /** 点名模式：每一轮的中签名单，按轮次升序 */
    private List<RoundVO> rounds;

    /** 分组模式：每一组的名单 */
    private List<GroupVO> groups;

    /** 一轮的结果 */
    @Data
    public static class RoundVO {
        private Integer round;
        private List<String> names;
    }

    /** 一组的结果 */
    @Data
    public static class GroupVO {
        private Integer groupNo;
        private List<String> names;
    }
}
