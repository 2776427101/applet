package com.banxing.classassistant.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 修改邀请码有效期。
 *
 * <p>三种形态，优先级 {@code days} &gt; {@code expireAt} &gt; 永久：
 * <ul>
 *   <li>{@code { "days": 7 }}：从现在起 7 天</li>
 *   <li>{@code { "expireAt": "2026-12-31 23:59:59" }}：指定截止时间</li>
 *   <li>{@code {}}：永久有效</li>
 * </ul>
 * 改有效期<b>不换邀请码</b>，所以码过期之后把时间改到未来，同一个码就能继续用。
 */
@Data
public class InviteExpireDTO {

    private Integer days;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expireAt;
}
