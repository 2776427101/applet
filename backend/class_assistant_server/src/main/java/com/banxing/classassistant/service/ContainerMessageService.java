package com.banxing.classassistant.service;

import com.banxing.classassistant.dto.MessageSendDTO;
import com.banxing.classassistant.dto.MessageVO;

import java.util.List;

/**
 * 群组聊天。
 *
 * <p>本期只做文本 + 轮询：项目没有 WebSocket 基础设施，
 * 为聊天单独引一套长连接（还要考虑小程序后台断连、重连退避）不划算。
 * 3 秒一次的增量拉取（{@code afterId}）在几十人的班级群里够用，
 * 而且天然幂等 —— 断网恢复后下一次轮询就把漏的消息补齐了。
 */
public interface ContainerMessageService {

    /**
     * 拉消息。三种用法：
     * <ul>
     *   <li>都不传：最近 {@code size} 条（进页面首次加载）</li>
     *   <li>传 {@code afterId}：比它新的（轮询增量）</li>
     *   <li>传 {@code beforeId}：比它旧的（上滑看历史）</li>
     * </ul>
     * 返回一律按 id 升序，前端直接 append 即可。
     */
    List<MessageVO> list(Long userId, Long containerId, Long afterId, Long beforeId, Integer size);

    /** 发消息。被禁言会抛 3005 */
    MessageVO send(Long userId, Long containerId, MessageSendDTO dto);
}
