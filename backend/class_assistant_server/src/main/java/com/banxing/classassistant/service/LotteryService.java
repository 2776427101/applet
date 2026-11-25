package com.banxing.classassistant.service;

import com.banxing.classassistant.dto.LotteryCardVO;
import com.banxing.classassistant.dto.LotteryCreateDTO;
import com.banxing.classassistant.dto.LotteryVO;

import java.util.List;
import java.util.Map;

/**
 * 抽签（随机点名 / 随机分组）。
 *
 * <p>和接龙最大的不同：<b>它不需要任何人参与</b>。候选名单在发布时就定死了，
 * 开奖只是从名单里取数，所以学生没有账号也能用。
 */
public interface LotteryService {

    /** 发起抽签。同时往群里发一条 type=6 的卡片消息 */
    LotteryVO create(Long userId, Long containerId, LotteryCreateDTO dto);

    /** 开奖。只有发起人 / 群主 / 班委能点 */
    LotteryVO draw(Long userId, Long containerId, Long lotteryId);

    /** 结果详情。{@code publicResult=0} 时，非发起人拿到的是空结果 */
    LotteryVO detail(Long userId, Long containerId, Long lotteryId);

    /** 批量取卡片状态，给聊天消息列表补 {@code lotteryCard} 用 */
    Map<Long, LotteryCardVO> cardMap(Long userId, Long containerId, List<Long> lotteryIds);
}
