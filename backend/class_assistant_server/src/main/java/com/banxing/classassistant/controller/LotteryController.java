package com.banxing.classassistant.controller;

import com.banxing.classassistant.common.Result;
import com.banxing.classassistant.dto.LotteryCreateDTO;
import com.banxing.classassistant.dto.LotteryVO;
import com.banxing.classassistant.service.LotteryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 抽签（通用，不挂 /teacher 前缀）。
 *
 * <p>只有三个接口：发起、开奖、看结果。
 * 没有"参与"接口 —— 抽签抽的是名单，不需要群成员点任何东西，
 * 这也是它在"学生还没有账号"的现状下就能用的原因。
 */
@RestController
@RequestMapping("/api/container/{id}/lottery")
@RequiredArgsConstructor
public class LotteryController {

    private final LotteryService lotteryService;

    /**
     * 发起抽签
     * POST /api/container/{id}/lottery
     * body: {"kind":1,"drawCount":3}
     *
     * <p>会同时往群里发一条 type=6 的卡片消息。
     * 名单在发布时快照，之后不再变 —— 现查名单会让历史结果对不上。
     */
    @PostMapping
    public Result<LotteryVO> create(
            HttpServletRequest request,
            @PathVariable Long id,
            @Valid @RequestBody LotteryCreateDTO dto) {

        Long userId = (Long) request.getAttribute("userId");
        return Result.success(lotteryService.create(userId, id, dto));
    }

    /**
     * 开奖
     * POST /api/container/{id}/lottery/{lotteryId}/draw
     *
     * <p>只有发起人（或群主/班委）能点。
     * 两人同时点：行锁 + 版本号，后到的那次会拿到同一个结果，不会各抽一批。
     */
    @PostMapping("/{lotteryId}/draw")
    public Result<LotteryVO> draw(
            HttpServletRequest request,
            @PathVariable Long id,
            @PathVariable Long lotteryId) {

        Long userId = (Long) request.getAttribute("userId");
        return Result.success(lotteryService.draw(userId, id, lotteryId));
    }

    /**
     * 结果详情
     * GET /api/container/{id}/lottery/{lotteryId}
     *
     * <p>{@code publicResult=0} 时，非发起人拿到的是空结果 ——
     * 判断在服务端，不靠前端"别渲染"来保护。
     */
    @GetMapping("/{lotteryId}")
    public Result<LotteryVO> detail(
            HttpServletRequest request,
            @PathVariable Long id,
            @PathVariable Long lotteryId) {

        Long userId = (Long) request.getAttribute("userId");
        return Result.success(lotteryService.detail(userId, id, lotteryId));
    }
}
