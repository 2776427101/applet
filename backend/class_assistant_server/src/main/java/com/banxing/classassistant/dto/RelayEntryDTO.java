package com.banxing.classassistant.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 接龙的一条填写。
 *
 * <p>两个入口共用这个 DTO：
 * <ul>
 *   <li>自助接龙：只传 {@code value}，学生是谁从 token 取</li>
 *   <li>老师代填：还要传 {@code studentId}，学生是谁由老师指定</li>
 * </ul>
 * 是否允许代填由 Service 判断（必须有 {@code studentId} 且是本人发起的接龙），
 * 不靠前端传什么就信什么。
 */
@Data
public class RelayEntryDTO {

    /** 填写内容；格式=序号 时可以不传 */
    @Size(max = 64, message = "填写内容最多 64 个字")
    private String value;

    /** 代填的学生 id。自助接龙时不要传，传了也不会被采用 */
    private Long studentId;
}
