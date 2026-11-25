package com.banxing.classassistant.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 设置/取消管理员。
 *
 * <p>只允许 2（班委）和 3（成员）两个值：
 * 1 是群主，本期不支持转让，所以接口层面直接不给这个选项 ——
 * 靠 {@code @Max(3)} 拦下来，比在 Service 里写 if 更早、更清楚。
 */
@Data
public class MemberRoleDTO {

    @NotNull(message = "请选择角色")
    @Min(value = 2, message = "角色只能是 2（班委）或 3（成员）")
    @Max(value = 3, message = "角色只能是 2（班委）或 3（成员）")
    private Integer role;
}
