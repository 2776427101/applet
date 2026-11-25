package com.banxing.classassistant.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 设置 / 修改 / 关闭加入密码。
 *
 * <p>{@code password} 传 null 或空串 = 关闭密码（之后任何人凭码即可加入）。
 * 不校验旧密码：操作人已经通过 JWT + 群主身份双重校验，
 * 再要一次旧密码只是在手机上多打一遍字；真正要防的是「手机被别人拿到」，
 * 那是登录态的问题，不是这个接口能解决的。
 */
@Data
public class JoinPasswordDTO {

    @Size(min = 6, max = 20, message = "加入密码需 6-20 位")
    private String password;
}
