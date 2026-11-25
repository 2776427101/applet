package com.banxing.classassistant.controller;

import com.banxing.classassistant.common.Result;
import com.banxing.classassistant.dto.RelayBatchDTO;
import com.banxing.classassistant.dto.RelayCardVO;
import com.banxing.classassistant.dto.RelayCreateDTO;
import com.banxing.classassistant.dto.RelayEntryDTO;
import com.banxing.classassistant.dto.RelayVO;
import com.banxing.classassistant.service.RelayService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 接龙（通用，不挂 /teacher 前缀）。
 *
 * <p>和群聊消息同一个理由：学生端上线后要复用同一批接口，
 * 放进 teacher 命名空间会让"学生能不能调"变成一个需要解释的问题。
 * 权限全在 Service 的 {@code requireMember} 里。
 *
 * <p>群 id 在路径上、接龙 id 也在路径上，是因为<b>它们确实是资源</b>；
 * 而"我是谁"永远从 token 取，不接受前端传 userId。
 */
@RestController
@RequestMapping("/api/container/{id}/relay")
@RequiredArgsConstructor
public class RelayController {

    private final RelayService relayService;

    /**
     * 发起接龙
     * POST /api/container/{id}/relay
     * body: {"title":"春游报名","entryFormat":1,"maxCount":30,"deadline":"2026-11-08 18:00:00"}
     *
     * <p>会同时往群里发一条 type=5 的卡片消息，不需要前端再调一次发消息。
     */
    @PostMapping
    public Result<RelayVO> create(
            HttpServletRequest request,
            @PathVariable Long id,
            @Valid @RequestBody RelayCreateDTO dto) {

        Long userId = (Long) request.getAttribute("userId");
        return Result.success(relayService.create(userId, id, dto));
    }

    /**
     * 接龙详情
     * GET /api/container/{id}/relay/{relayId}
     *
     * <p>一次拿全：状态、名单、代填候选、我的填写。
     * 拆成三个接口的话，详情页任何一环失败就整页空着。
     */
    @GetMapping("/{relayId}")
    public Result<RelayVO> detail(
            HttpServletRequest request,
            @PathVariable Long id,
            @PathVariable Long relayId) {

        Long userId = (Long) request.getAttribute("userId");
        return Result.success(relayService.detail(userId, id, relayId));
    }

    /**
     * 自助接龙
     * POST /api/container/{id}/relay/{relayId}/entry
     * body: {"value":"3"}   ← 格式=序号 时可以不传
     *
     * <p>重复提交不报错，返回已有记录。
     * 满员 4101 / 已截止 4102 / 已结束 4103。
     */
    @PostMapping("/{relayId}/entry")
    public Result<RelayCardVO> join(
            HttpServletRequest request,
            @PathVariable Long id,
            @PathVariable Long relayId,
            @Valid @RequestBody RelayEntryDTO dto) {

        Long userId = (Long) request.getAttribute("userId");
        return Result.success(relayService.join(userId, id, relayId, dto));
    }

    /**
     * 代填补录
     * POST /api/container/{id}/relay/{relayId}/entry/batch
     * body: {"entries":[{"studentId":12,"value":""},{"studentId":13}]}
     *
     * <p>整批一个事务：中途有一条重复或超员，全部回滚。
     * 部分成功比全失败更难解释 —— 老师没法知道到底哪几条进去了。
     */
    @PostMapping("/{relayId}/entry/batch")
    public Result<RelayCardVO> batch(
            HttpServletRequest request,
            @PathVariable Long id,
            @PathVariable Long relayId,
            @Valid @RequestBody RelayBatchDTO dto) {

        Long userId = (Long) request.getAttribute("userId");
        return Result.success(relayService.batch(userId, id, relayId, dto));
    }

    /**
     * 修改一条记录
     * PUT /api/container/{id}/relay/{relayId}/entry/{entryId}
     *
     * <p>只能改自己填的那条；代填行可以由发起人改。
     */
    @PutMapping("/{relayId}/entry/{entryId}")
    public Result<RelayCardVO> updateEntry(
            HttpServletRequest request,
            @PathVariable Long id,
            @PathVariable Long relayId,
            @PathVariable Long entryId,
            @Valid @RequestBody RelayEntryDTO dto) {

        Long userId = (Long) request.getAttribute("userId");
        return Result.success(relayService.updateEntry(userId, id, relayId, entryId, dto));
    }

    /**
     * 结束接龙
     * POST /api/container/{id}/relay/{relayId}/close
     *
     * <p>只有发起人（或群主/班委）能结束。结束之后卡片变灰，不能再填。
     */
    @PostMapping("/{relayId}/close")
    public Result<RelayCardVO> close(
            HttpServletRequest request,
            @PathVariable Long id,
            @PathVariable Long relayId) {

        Long userId = (Long) request.getAttribute("userId");
        return Result.success(relayService.close(userId, id, relayId));
    }
}
