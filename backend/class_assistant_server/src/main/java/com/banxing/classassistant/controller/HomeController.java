package com.banxing.classassistant.controller;

import com.banxing.classassistant.common.Result;
import com.banxing.classassistant.dto.HomeNoticeListVO;
import com.banxing.classassistant.dto.HomeOverviewVO;
import com.banxing.classassistant.service.HomeService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
     *
     * GET /api/teacher/home/overview?classId=2
     * classId 不传 = 我的第一个班（兼容旧前端）
     */
    @GetMapping("/overview")
    public Result<HomeOverviewVO> overview(
            HttpServletRequest request,
            @RequestParam(required = false) Long classId) {
        // 从 request 属性取用户 ID（拦截器已解析并校验过 token）
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(homeService.overview(userId, classId));
    }

    /**
     * 首页通知
     * 待审批请假 / 今天到期的待办 / 今天生日，按"该不该处理"排序
     *
     * GET /api/teacher/home/notices?classId=2&limit=20
     *
     * <p><b>classId 不传 = 我的所有班（v0.44 改了语义）。</b>
     * 原来不传等于"第一个班"，结果老师带两个班时必须切到二班才看得见二班的请假申请，
     * 切回来一班的那条又消失了 —— 提醒变成了"当前标签页的通知"。
     * 通知和首页数字不是一回事：数字看当前班，通知跨班。
     *
     * <p>每条通知带 {@code classId} / {@code className}，聚合之后才知道是哪个班的。
     */
    @GetMapping("/notices")
    public Result<HomeNoticeListVO> notices(
            HttpServletRequest request,
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) Integer limit) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(homeService.notices(userId, classId, limit));
    }
}
