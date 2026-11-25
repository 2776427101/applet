package com.banxing.classassistant.service;

import com.banxing.classassistant.dto.AidiActDTO;
import com.banxing.classassistant.dto.AidiChatDTO;
import com.banxing.classassistant.dto.AidiReplyVO;

/**
 * 乐迪（AI 小助手）
 */
public interface AidiService {

    /**
     * 问一句，答一句
     *
     * <p>如果这一轮模型想动数据，返回里的 {@code action} 非空 —— 但那只是「提议」，
     * 什么都没改。真正执行走 {@link #act}。
     *
     * @param userId 当前登录老师（从 token 来，不接受前端传）
     * @param dto    班级 + 对话历史
     * @return 回答文本 + 待确认动作
     */
    AidiReplyVO chat(Long userId, AidiChatDTO dto);

    /**
     * 执行老师确认过的动作
     *
     * <p>参数由前端原样回传，后端<b>重新校验归属</b>后才落库 ——
     * 不因为"它来自我们自己的前端"就信任它。
     *
     * @return reply 里是一句执行结果，如「已把「收家长回执」标为完成」
     */
    AidiReplyVO act(Long userId, AidiActDTO dto);
}
