package com.banxing.classassistant.service;

import com.banxing.classassistant.entity.ContainerOpLog;
import com.banxing.classassistant.mapper.ContainerOpLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 容器操作日志写入。
 *
 * <p>单独抽出来是因为「改配置」「成员管理」「加入链路」三个 Service 都要写日志，
 * 各抄一遍 {@code new ContainerOpLog()} 迟早会漏字段。
 *
 * <p>{@code detail} 由调用方拼固定文案，<b>不要把用户输入原样塞进来</b> ——
 * 有人会在「原因」里写密码，日志是要留档的。
 */
@Service
@RequiredArgsConstructor
public class ContainerLogService {

    /** detail 字段是 VARCHAR(255)，留 50 字余量给前缀文案 */
    private static final int DETAIL_MAX = 200;

    private final ContainerOpLogMapper opLogMapper;

    /**
     * 写一条日志。
     *
     * @param targetUserId 被操作人；改配置类操作传 null
     */
    public void write(Long containerId, Long operatorId, Long targetUserId, String actionType, String detail) {
        ContainerOpLog log = new ContainerOpLog();
        log.setContainerId(containerId);
        log.setOperatorId(operatorId);
        log.setTargetUserId(targetUserId);
        log.setActionType(actionType);
        log.setDetail(detail == null ? null : (detail.length() > DETAIL_MAX ? detail.substring(0, DETAIL_MAX) : detail));
        opLogMapper.insert(log);
    }
}
