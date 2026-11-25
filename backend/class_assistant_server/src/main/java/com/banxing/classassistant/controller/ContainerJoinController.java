package com.banxing.classassistant.controller;

import com.banxing.classassistant.common.Result;
import com.banxing.classassistant.dto.JoinDTO;
import com.banxing.classassistant.dto.JoinPreviewVO;
import com.banxing.classassistant.dto.JoinResultVO;
import com.banxing.classassistant.service.ContainerJoinService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 加入链路（通用，不挂 /teacher 前缀）。
 *
 * <p>放在这里而不是 /api/teacher/container 下，是因为它<b>不要求是老师</b>：
 * 任何登录用户都能凭邀请码加入。本期学生端还没做，实际只有老师会用，
 * 但路径和权限模型已经按"角色无关"设计好 —— 学生端上线后页面直接复用，
 * 不需要改后端、也不需要迁移接口。
 *
 * <p>鉴权：仍然走 {@code /api/**} 的 AuthInterceptor，必须带 token。
 * 邀请码本身不能当凭证用，否则任何人都能拿一个码去探测容器信息。
 */
@RestController
@RequestMapping("/api/container")
@RequiredArgsConstructor
public class ContainerJoinController {

    private final ContainerJoinService joinService;

    /**
     * 加入前预览
     * GET /api/container/preview?code=K7M2QX9B4T
     *
     * 不抛"已过期"异常，把 expired 作为字段返回，页面才好展示。
     */
    @GetMapping("/preview")
    public Result<JoinPreviewVO> preview(HttpServletRequest request, @RequestParam String code) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(joinService.preview(userId, code));
    }

    /**
     * 加入容器
     * POST /api/container/join
     * body: {"code":"K7M2QX9B4T","password":"math2026"}
     */
    @PostMapping("/join")
    public Result<JoinResultVO> join(HttpServletRequest request, @Valid @RequestBody JoinDTO dto) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(joinService.join(userId, dto));
    }

    /**
     * 主动退出
     * POST /api/container/{id}/leave
     *
     * 群主不能退出（要先解散），所以这里是 POST 而不是 DELETE ——
     * 它不是"删除这个容器"，只是"我退出这个容器"。
     */
    @PostMapping("/{id}/leave")
    public Result<Void> leave(HttpServletRequest request, @PathVariable Long id) {
        Long userId = (Long) request.getAttribute("userId");
        joinService.leave(userId, id);
        return Result.success(null);
    }
}
