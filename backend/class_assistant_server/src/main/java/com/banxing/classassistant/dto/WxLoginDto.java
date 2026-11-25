package com.banxing.classassistant.dto;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
/**
 * 微信登录请求参数
 * 前端调用 wx.login() 获取临时 code 后，通过此 DTO 提交到后端，
 * 后端再用该 code 向微信服务器换取用户的 openid 和 session_key。
 */
@Data
public class WxLoginDto {
    /**
     * 微信授权临时票据，由前端 wx.login() 返回
     * 后端凭此 code 调用微信 jscode2session 接口完成身份验证
     * 为安全考虑，code 为一次性且有效期仅 5 分钟，不可为空
     */
    // 简单来说这里创建了code字段 NotBlank注解限制不能为空
    @NotBlank(message = "code不能为空")
    private String code;
}
