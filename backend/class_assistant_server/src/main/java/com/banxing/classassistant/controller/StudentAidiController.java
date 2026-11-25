package com.banxing.classassistant.controller;

import com.banxing.classassistant.common.Result;
import com.banxing.classassistant.dto.AidiReplyVO;
import com.banxing.classassistant.dto.StudentAidiChatDTO;
import com.banxing.classassistant.service.StudentAidiService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学生端乐迪
 *
 * <p>路径放在 /api/student 下面，所以拦截器按路径分权时自动只放 role=2 进来，
 * 不需要额外判断。学生拿不到老师那套工具，也调不到 /api/teacher/aidi/*。
 */
@RestController
@RequestMapping("/api/student/aidi")
@RequiredArgsConstructor
public class StudentAidiController {

    private final StudentAidiService studentAidiService;

    /**
     * 问乐迪
     * POST /api/student/aidi/chat
     * body: { "messages": [{ "role": "user", "content": "我上次考多少" }] }
     */
    @PostMapping("/chat")
    public Result<AidiReplyVO> chat(HttpServletRequest request,
                                    @RequestBody StudentAidiChatDTO dto) {
        return Result.success(studentAidiService.chat(
                (Long) request.getAttribute("classId"),
                (Long) request.getAttribute("studentId"),
                dto));
    }
}
