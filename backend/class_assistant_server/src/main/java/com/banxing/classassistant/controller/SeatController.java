package com.banxing.classassistant.controller;

import com.banxing.classassistant.common.Result;
import com.banxing.classassistant.dto.SeatChartVO;
import com.banxing.classassistant.dto.SeatSaveDTO;
import com.banxing.classassistant.service.SeatService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 座位接口
 *
 * <p>只有两个：读整图、写整图。没有"移动一个座位"的接口，
 * 因为前端手里本来就有整张图，整图覆盖比单点移动少一半出错可能。
 */
@RestController
@RequestMapping("/api/teacher/seat")
@RequiredArgsConstructor
public class SeatController {

    private final SeatService seatService;

    /**
     * 读座位图
     * GET /api/teacher/seat/chart
     */
    @GetMapping("/chart")
    public Result<SeatChartVO> chart(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(seatService.chart(userId));
    }

    /**
     * 保存座位图（整图覆盖）
     * PUT /api/teacher/seat/chart
     * body: { "rows":6, "cols":8, "seats":[ {"row":1,"col":1,"studentId":1} ] }
     *
     * <p>用 PUT 不是 POST：同一个 body 反复提交，结果完全一样（幂等），
     * 这正是 PUT 的语义。小程序端 method 白名单里也有 PUT，没有 PATCH。
     */
    @PutMapping("/chart")
    public Result<Map<String, Integer>> save(HttpServletRequest request,
                                             @RequestBody SeatSaveDTO dto) {
        Long userId = (Long) request.getAttribute("userId");
        int seated = seatService.save(userId, dto);
        return Result.success(Map.of("saved", seated));
    }
}
