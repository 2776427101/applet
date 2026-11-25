package com.banxing.classassistant.config;

import com.banxing.classassistant.interceptor.AuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;
/**
 * Web 配置类
 * 配置拦截器 + 群聊附件的静态访问
 */
// 用 @Configuration 注解标记这是一个配置类
@Configuration
// 用 @RequiredArgsConstructor 注解生成一个构造器，自动注入 AuthInterceptor
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {
    // 需要注册的鉴权拦截器，通过构造器由 Spring 容器注入
    private final AuthInterceptor authInterceptor;

    /** 群聊附件落盘目录，和 ContainerFileServiceImpl 用的是同一个配置项 */
    @Value("${app.upload.dir}")
    private String uploadDir;

    /**
     * 注册拦截器（WebMvcConfigurer 提供的回调，Spring 启动时自动调用）
     * @param registry 拦截器注册表，用于登记拦截器并配置其生效范围
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)          // 添加鉴权拦截器
                .addPathPatterns("/api/**")                   // 拦截所有api请求路径
                /* 两个登录接口都要放行：登录前本来就没有 token。
                   漏掉学生那个的话，学生会卡在"请先登录"进不来 */
                .excludePathPatterns("/api/auth/wx-login", "/api/auth/student-login");
    }

    /**
     * 把上传目录映射成 /files/** 静态资源。
     *
     * <p>为什么不放在 /api 下：小程序里 {@code <image src>} / {@code <video src>}
     * 由原生组件发请求，<b>带不上 Authorization 头</b>。放在 /api/** 会被
     * AuthInterceptor 拦成 401，图片永远显示不出来。所以附件走独立前缀，
     * 靠"路径里的 uuid 猜不到"来保护，而不是靠 token。
     *
     * <p>路径用 {@code toUri()} 生成：Windows 的反斜杠会被自动转成正斜杠。
     * 手拼 {@code "file:" + 相对目录} 在 Windows 上得到的是带反斜杠的串，
     * Spring 解析不出来，表现是图片全部 404。
     * <b>注意注释里也不要写「反斜杠 + u」这种组合</b> —— Java 会把它当 Unicode 转义，
     * 直接编译报"非法的 Unicode 逃逸"。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path dir = Paths.get(uploadDir).toAbsolutePath().normalize();
        registry.addResourceHandler("/files/**")
                .addResourceLocations(dir.toUri().toString());
    }
}
