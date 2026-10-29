package com.banxing.classassistant.controller;

import com.banxing.classassistant.common.Result;
import com.banxing.classassistant.dto.ScoreBatchDTO;
import com.banxing.classassistant.dto.ScoreListVO;
import com.banxing.classassistant.dto.ScoreStatsVO;
import com.banxing.classassistant.service.ScoreService;
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
 * 成绩接口
 */
@RestController
@RequestMapping("/api/teacher/score")
@RequiredArgsConstructor
public class ScoreController {

    private final ScoreService scoreService;

    /**
     * 某次考试的成绩表
     * GET /api/teacher/score/list?examId=2
     */
    @GetMapping("/list")
    public Result<ScoreListVO> list(
            HttpServletRequest request,
            @RequestParam(required = false) Long examId) {

        Long userId = (Long) request.getAttribute("userId");
        return Result.success(scoreService.list(userId, examId));
    }

    /**
     * 批量录入成绩
     * POST /api/teacher/score/batch
     * body: { "examId":2, "rows":[ {"studentId":1,"scores":{"语文":128,"数学":null}} ] }
     */
    @PostMapping("/batch")
    public Result<Map<String, Integer>> batch(
            HttpServletRequest request,
            @RequestBody ScoreBatchDTO dto) {

        Long userId = (Long) request.getAttribute("userId");
        int saved = scoreService.batchSave(userId, dto);
        return Result.success(Map.of("saved", saved));
    }

    /**
     * 成绩统计
     * GET /api/teacher/score/stats?examId=2
     */
    @GetMapping("/stats")
    public Result<ScoreStatsVO> stats(
            HttpServletRequest request,
            @RequestParam(required = false) Long examId) {

        Long userId = (Long) request.getAttribute("userId");
        return Result.success(scoreService.stats(userId, examId));
    }
}
