package com.banxing.classassistant.controller;

import com.banxing.classassistant.common.Result;
import com.banxing.classassistant.dto.HomeOverviewVO;
import com.banxing.classassistant.service.HomeService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 教师首页接口
 */
@RestController
// 公共URL前缀
@RequestMapping("/api/teacher/home")
// 自动生成构造函数
@RequiredArgsConstructor
public class HomeController {
    private final HomeService homeService;
    /**
     * 首页聚合数据
     * 一次返回班级信息、四个统计、学期周次、金句
     */
    @GetMapping("/overview")
    public Result<HomeOverviewVO> overview(HttpServletRequest request) {
        // 从 request 属性取用户 ID（拦截器已解析并校验过 token）
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(homeService.overview(userId));
    }
}
