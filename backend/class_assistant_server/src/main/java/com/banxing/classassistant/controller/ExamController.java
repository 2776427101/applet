package com.banxing.classassistant.controller;

import com.banxing.classassistant.common.Result;
import com.banxing.classassistant.dto.ExamCreateDTO;
import com.banxing.classassistant.dto.ExamListVO;
import com.banxing.classassistant.service.ExamService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 考试接口
 */
@RestController
@RequestMapping("/api/teacher/exam")
@RequiredArgsConstructor
public class ExamController {

    private final ExamService examService;

    /**
     * 考试列表
     * GET /api/teacher/exam/list?page=1&size=20
     */
    @GetMapping("/list")
    public Result<ExamListVO> list(
            HttpServletRequest request,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {

        Long userId = (Long) request.getAttribute("userId");
        return Result.success(examService.list(userId, page, size));
    }

    /**
     * 新建考试
     * POST /api/teacher/exam
     * body: { "name":"第一次月考", "type":2, "examDate":"2026-10-10",
     *         "subjects":["语文","数学","英语"], "fullScore":{"语文":150} }
     */
    @PostMapping
    public Result<Map<String, Long>> create(
            HttpServletRequest request,
            @RequestBody ExamCreateDTO dto) {

        Long userId = (Long) request.getAttribute("userId");
        Long id = examService.create(userId, dto);
        return Result.success(Map.of("id", id));
    }
}
