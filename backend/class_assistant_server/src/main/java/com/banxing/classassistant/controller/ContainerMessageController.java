package com.banxing.classassistant.controller;

import com.banxing.classassistant.common.Result;
import com.banxing.classassistant.dto.MessageSendDTO;
import com.banxing.classassistant.dto.MessageVO;
import com.banxing.classassistant.dto.UploadVO;
import com.banxing.classassistant.service.ContainerFileService;
import com.banxing.classassistant.service.ContainerMessageService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 群组聊天（通用，不挂 /teacher 前缀）。
 *
 * <p>和加入链路同一个理由：学生端上线后要复用同一批接口，
 * 放进 teacher 命名空间会让"学生能不能调"变成一个需要解释的问题。
 * 权限判断全在 Service：{@code requireMember} / {@code assertCanSpeak}。
 *
 * <p>和 {@link ContainerJoinController} 共用 {@code /api/container} 前缀：
 * Spring 允许，路径不重叠（那边是 /preview、/join、/{id}/leave）。
 */
@RestController
@RequestMapping("/api/container")
@RequiredArgsConstructor
public class ContainerMessageController {

    private final ContainerMessageService messageService;
    private final ContainerFileService fileService;

    /**
     * 拉消息
     * GET /api/container/{id}/messages?afterId=0&beforeId=0&size=30
     *
     * @param afterId  只取比它新的（前端轮询用，增量）
     * @param beforeId 只取比它旧的（上滑看历史）
     * @param size     默认 30，上限 50
     */
    @GetMapping("/{id}/messages")
    public Result<List<MessageVO>> list(
            HttpServletRequest request,
            @PathVariable Long id,
            @RequestParam(required = false) Long afterId,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Integer size) {

        Long userId = (Long) request.getAttribute("userId");
        return Result.success(messageService.list(userId, id, afterId, beforeId, size));
    }

    /**
     * 发消息
     * POST /api/container/{id}/messages
     * body: {"content":"明天带练习册"}
     *
     * 被禁言返回 3005，前端据此把输入框置灰。
     */
    @PostMapping("/{id}/messages")
    public Result<MessageVO> send(
            HttpServletRequest request,
            @PathVariable Long id,
            @Valid @RequestBody MessageSendDTO dto) {

        Long userId = (Long) request.getAttribute("userId");
        return Result.success(messageService.send(userId, id, dto));
    }

    /**
     * 上传附件（图片 / 视频 / 文件）
     * POST /api/container/{id}/upload  (multipart/form-data)
     *
     * @param file 表单字段名固定为 file
     * @param kind image / video / file，决定扩展名白名单和大小上限，默认 image
     * @return { url, name, size }，url 是相对路径，前端拼上域名再用
     *
     * <p><b>上传和发消息是两步</b>：先传拿到 url，再带着 url 发一条 type=2/3/4 的消息。
     * 合成一步的话，用户选了图又取消发送，文件已经存下来了却没法回收。
     *
     * <p>被禁言的人能不能上传？这里只校验 {@code requireMember}，不校验禁言 ——
     * 传上来但没有消息引用它，等于没发出去；而禁言要拦的是"发出去"这个动作，
     * 那个判断在 {@code send()} 里。少一次重复校验，也少一个"禁言的人传完才发现发不出去"的坑。
     */
    @PostMapping("/{id}/upload")
    public Result<UploadVO> upload(
            HttpServletRequest request,
            @PathVariable Long id,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false, defaultValue = "image") String kind) {

        Long userId = (Long) request.getAttribute("userId");
        return Result.success(fileService.upload(userId, id, file, kind));
    }
}
