package com.banxing.classassistant.service;

import com.banxing.classassistant.dto.RelayBatchDTO;
import com.banxing.classassistant.dto.RelayCardVO;
import com.banxing.classassistant.dto.RelayCreateDTO;
import com.banxing.classassistant.dto.RelayEntryDTO;
import com.banxing.classassistant.dto.RelayVO;

import java.util.List;
import java.util.Map;

/**
 * 接龙。
 *
 * <p>所有方法的第一个参数都是当前登录用户，且实现里第一行必须是一次
 * {@code auth.requireMember} —— 和容器相关的其它 Service 同一条规矩。
 *
 * <p>发起人之外的人只能"填"和"看"，不能结束、不能取消、不能代填。
 * 这条边界如果只写在前端按钮的显隐上，等于没有。
 */
public interface RelayService {

    /** 发起接龙。同时往群里发一条 type=5 的卡片消息 */
    RelayVO create(Long userId, Long containerId, RelayCreateDTO dto);

    /** 接龙详情（含名单、代填候选、我的填写） */
    RelayVO detail(Long userId, Long containerId, Long relayId);

    /** 自助接龙。重复提交返回已有记录，不报错 */
    RelayCardVO join(Long userId, Long containerId, Long relayId, RelayEntryDTO dto);

    /** 代填补录（发起人 / 群主 / 班委） */
    RelayCardVO batch(Long userId, Long containerId, Long relayId, RelayBatchDTO dto);

    /** 修改一条记录（自己的，或发起人改代填行） */
    RelayCardVO updateEntry(Long userId, Long containerId, Long relayId, Long entryId, RelayEntryDTO dto);

    /** 结束接龙（发起人 / 群主） */
    RelayCardVO close(Long userId, Long containerId, Long relayId);

    /**
     * 批量取卡片状态，给聊天消息列表补 {@code relayCard} 用。
     * 一次查询解决 N 条消息，不在循环里查库。
     */
    Map<Long, RelayCardVO> cardMap(Long userId, Long containerId, List<Long> relayIds);
}
