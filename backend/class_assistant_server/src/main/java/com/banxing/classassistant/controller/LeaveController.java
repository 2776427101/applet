package com.banxing.classassistant.controller;

import com.banxing.classassistant.common.Result;
import com.banxing.classassistant.dto.LeaveAuditDTO;
import com.banxing.classassistant.dto.LeaveCreateDTO;
import com.banxing.classassistant.dto.LeaveListVO;
import com.banxing.classassistant.service.LeaveService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 请假接口
 */
@RestController
@RequestMapping("/api/teacher/leave")
@RequiredArgsConstructor
public class LeaveController {

    private final LeaveService leaveService;

    /**
     * 请假列表
     * GET /api/teacher/leave/list?status=0&page=1&size=20
     *
     * 三个参数都可选：不传 status = 全部，不传 page/size = 第 1 页 20 条
     */
    @GetMapping("/list")
    public Result<LeaveListVO> list(
            HttpServletRequest request,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {

        Long userId = (Long) request.getAttribute("userId");
        return Result.success(leaveService.list(userId, status, page, size));
    }

    /**
     * 审批
     * PUT /api/teacher/leave/5/audit
     * body: { "status": 1, "remark": "已电话确认家长，同意" }
     */
    @PutMapping("/{id}/audit")
    public Result<Void> audit(
            HttpServletRequest request,
            @PathVariable Long id,
            @RequestBody LeaveAuditDTO dto) {

        Long userId = (Long) request.getAttribute("userId");
        leaveService.audit(userId, id, dto);
        return Result.success(null);
    }

    /**
     * 代学生请假
     * POST /api/teacher/leave
     * body: { "studentId":1, "type":1, "startAt":"...", "endAt":"...", "reason":"..." }
     */
    @PostMapping
    public Result<Map<String, Long>> add(
            HttpServletRequest request,
            @RequestBody LeaveCreateDTO dto) {

        Long userId = (Long) request.getAttribute("userId");
        Long id = leaveService.add(userId, dto);
        return Result.success(Map.of("id", id));
    }
}
