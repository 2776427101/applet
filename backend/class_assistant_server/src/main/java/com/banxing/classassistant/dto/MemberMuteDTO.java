package com.banxing.classassistant.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 禁言 / 解除禁言。
 *
 * <p>{@code muteMinutes} 的三种语义：
 * <ul>
 *   <li>{@code > 0}：禁言 N 分钟（上限 30 天，再长请用 -1）</li>
 *   <li>{@code -1}：永久禁言（存 9999-12-31 23:59:59）</li>
 *   <li>{@code 0} 或 {@code null}：解除禁言</li>
 * </ul>
 * 用一个数字表达三态，前端只需要一个「时长」选择器，不用额外传 isPermanent。
 */
@Data
public class MemberMuteDTO {

    private Integer muteMinutes;

    /** 禁言原因必填；解除禁言时忽略 */
    @Size(max = 100, message = "原因最多 100 个字")
    private String reason;
}
