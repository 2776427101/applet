package com.banxing.classassistant.interceptor;

import com.banxing.classassistant.common.BizException;
import com.banxing.classassistant.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录鉴权拦截器
 * 在请求进入 Controller 之前校验 JWT 令牌，确认用户已登录并解析出用户 ID
 */
// 把这个类注册成 Spring Bean，交给 IoC 容器管理，才能被拦截器配置注入使用
@Component
// Lombok 注解：为所有 final 字段自动生成构造器，实现 JwtUtil 的依赖注入
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {
    // 用于解析 JWT 令牌的工具类，通过构造器注入
    private final JwtUtil jwtUtil;
    /**
     * preHandle 在目标 Controller 方法执行之前调用
     * @return true 放行请求，false 中断请求
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 从请求头中获取 Authorization 字段，小程序登录后通常携带 "Bearer <token>"
        String auth = request.getHeader("Authorization");
        // 令牌缺失或格式不正确（未以 "Bearer " 开头），视为未登录，抛出业务异常
        if (auth == null || !auth.startsWith("Bearer ")){
            throw new BizException(1002,"请先登录");
        }
        // 截取 "Bearer " 之后（第 7 位起）的字符串作为 token，解析出用户 ID
        Long userId = jwtUtil.parseUserId(auth.substring(7));
        // 将用户 ID 存入 request 作用域，后续 Controller 可直接读取，避免重复解析
        request.setAttribute("userId", userId);
        // 校验通过，放行请求
        return true;
    }
}
