package com.banxing.classassistant.controller;

import com.banxing.classassistant.common.Result;
import com.banxing.classassistant.dto.LoginVO;
import com.banxing.classassistant.dto.WxLoginDto;
import com.banxing.classassistant.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录相关接口
 */
// 被Spring扫描后注册为Bean且类中所有方法的返回值都直接序列化为JSON写入响应体，而不是当作视图名去跳转页面。
@RestController
// 公共URL前缀
@RequestMapping("/api/auth")
// 自动生成构造函数
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    /**
     * 微信登录
     * @param dto 只包含一个 code
     * @return token + 用户信息
     */
    @PostMapping("/wx-login")
    public Result<LoginVO> wxLogin(@RequestBody @Valid WxLoginDto dto) {
        // @Valid 会触发 WxLoginDto 里的 @NotBlank 校验，不通过时由全局异常处理器返回 1001
        return Result.success(authService.login(dto.getCode()));
    }
    /**
     * 查询当前登录用户（小程序冷启动时用它校验 token 是否还有效）
     */
    @GetMapping("/me")
    public Result<LoginVO.UserVO> me(HttpServletRequest request) {
        // userId 是拦截器 preHandle 里塞进来的，这里直接取，不用再解析一次 token
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(authService.currentUser(userId));
    }
}
