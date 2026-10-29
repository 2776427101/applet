package com.banxing.classassistant.config;

import com.banxing.classassistant.interceptor.AuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
/**
 * Web 配置类
 * 配置拦截器
 */
// 用 @Configuration 注解标记这是一个配置类
@Configuration
// 用 @RequiredArgsConstructor 注解生成一个构造器，自动注入 AuthInterceptor
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {
    // 需要注册的鉴权拦截器，通过构造器由 Spring 容器注入
    private final AuthInterceptor authInterceptor;
    /**
     * 注册拦截器（WebMvcConfigurer 提供的回调，Spring 启动时自动调用）
     * @param registry 拦截器注册表，用于登记拦截器并配置其生效范围
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)          // 添加鉴权拦截器
                .addPathPatterns("/api/**")                   // 拦截所有api请求路径
                .excludePathPatterns("/api/auth/wx-login"); // 放行微信登录接口（登录前无 token）
    }

}
