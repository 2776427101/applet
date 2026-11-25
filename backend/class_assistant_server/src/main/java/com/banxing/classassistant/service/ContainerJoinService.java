package com.banxing.classassistant.service;

import com.banxing.classassistant.dto.JoinDTO;
import com.banxing.classassistant.dto.JoinPreviewVO;
import com.banxing.classassistant.dto.JoinResultVO;

/**
 * 加入链路：预览、加入、退出。
 *
 * <p>和 {@link ContainerService} 分开的原因：这条链路<b>不要求是老师</b>，
 * 任何已登录用户都能调。本期学生端还没做，所以真实用户只有老师，
 * 但接口本身按"角色无关"写 —— 学生端上线后直接复用，不用改后端。
 */
public interface ContainerJoinService {

    JoinPreviewVO preview(Long userId, String code);

    JoinResultVO join(Long userId, JoinDTO dto);

    void leave(Long userId, Long containerId);
}
