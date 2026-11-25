package com.banxing.classassistant.controller;

import com.banxing.classassistant.common.Result;
import com.banxing.classassistant.dto.DirectMessageThreadVO;
import com.banxing.classassistant.dto.DirectMessageVO;
import com.banxing.classassistant.dto.DirectThreadBriefVO;
import com.banxing.classassistant.dto.SendMessageDTO;
import com.banxing.classassistant.service.DirectMessageService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 老师给学生发私信
 *
 * <p>路径挂在 /api/teacher 下，拦截器会强制要求 role=1（老师）。
 * 学生那边用的是 /api/student/message，两边永远走不到同一个方法上。
 */
@RestController
@RequestMapping("/api/teacher/message")
@RequiredArgsConstructor
public class TeacherMessageController {

    private final DirectMessageService directMessageService;

    /**
     * 我的私信会话列表（通讯录顶部那一区）
     * GET /api/teacher/message
     *
     * <p>挂在根路径而不是 {@code /list}：下面有 {@code /{studentId}}，
     * 任何字面量段都会被当成 studentId 试一次，虽然 Spring 会让字面量优先匹配，
     * 但让路径形状本身不产生歧义更省心。
     */
    @GetMapping
    public Result<List<DirectThreadBriefVO>> threads(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(directMessageService.recentThreads(userId));
    }

    /**
     * 拉和某个学生的对话
     * GET /api/teacher/message/{studentId}
     */
    @GetMapping("/{studentId}")
    public Result<DirectMessageThreadVO> thread(HttpServletRequest request,
                                               @PathVariable Long studentId) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(directMessageService.teacherThread(userId, studentId));
    }

    /**
     * 发一条
     * POST /api/teacher/message/{studentId}
     * body: { "content": "..." }
     */
    @PostMapping("/{studentId}")
    public Result<DirectMessageVO> send(HttpServletRequest request,
                                        @PathVariable Long studentId,
                                        @RequestBody @Valid SendMessageDTO dto) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(directMessageService.sendByTeacher(userId, studentId, dto.getContent()));
    }

    /**
     * 标为已读（老师打开对话时调）
     * POST /api/teacher/message/{studentId}/read
     */
    @PostMapping("/{studentId}/read")
    public Result<Integer> read(HttpServletRequest request, @PathVariable Long studentId) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(directMessageService.markReadByTeacher(userId, studentId));
    }
}
