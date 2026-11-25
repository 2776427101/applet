package com.banxing.classassistant.controller;

import com.banxing.classassistant.common.Result;
import com.banxing.classassistant.dto.AidiActDTO;
import com.banxing.classassistant.dto.AidiChatDTO;
import com.banxing.classassistant.dto.AidiReplyVO;
import com.banxing.classassistant.service.AidiService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 乐迪（AI 小助手）
 */
@RestController
@RequestMapping("/api/teacher/aidi")
@RequiredArgsConstructor
public class AidiController {

    private final AidiService aidiService;

    /**
     * 问乐迪一句话
     * POST /api/teacher/aidi/chat
     * body: { "classId":1, "messages":[{"role":"user","content":"张三的成绩怎么样"}] }
     *
     * <p>为什么是 POST 而不是 GET：一次问答的 body 里带整段历史，
     * 塞进 query string 很快就会超过长度限制，而且会被日志原样记下来。
     */
    @PostMapping("/chat")
    public Result<AidiReplyVO> chat(HttpServletRequest request, @RequestBody AidiChatDTO dto) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(aidiService.chat(userId, dto));
    }

    /**
     * 执行老师确认过的动作
     * POST /api/teacher/aidi/act
     * body: { "classId":1, "action":{ "type":"complete_todo", "params":{...} } }
     *
     * <p>单独一个接口而不是塞回 /chat：这一步不该再经过模型。
     * 模型只负责提议，执行是一次确定的、可审计的写操作。
     */
    @PostMapping("/act")
    public Result<AidiReplyVO> act(HttpServletRequest request, @RequestBody AidiActDTO dto) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(aidiService.act(userId, dto));
    }
}
