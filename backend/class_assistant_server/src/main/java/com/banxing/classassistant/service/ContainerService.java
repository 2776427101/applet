package com.banxing.classassistant.service;

import com.banxing.classassistant.dto.ContainerCreateDTO;
import com.banxing.classassistant.dto.ContainerCreateVO;
import com.banxing.classassistant.dto.ContainerDetailVO;
import com.banxing.classassistant.dto.ContainerListVO;
import com.banxing.classassistant.dto.ContainerLogVO;
import com.banxing.classassistant.dto.ContainerUpdateDTO;
import com.banxing.classassistant.dto.DissolveDTO;
import com.banxing.classassistant.dto.InviteExpireDTO;
import com.banxing.classassistant.dto.InviteVO;
import com.banxing.classassistant.dto.JoinPasswordDTO;

import java.util.List;

/**
 * 学科容器的管理侧业务：创建、列表、详情、改信息、解散、邀请码、加入密码、日志。
 *
 * <p>成员管理与加入链路分别在 {@link ContainerMemberService} / {@link ContainerJoinService}，
 * 拆开的理由是三者权限模型不同：这里几乎全是群主操作，成员管理是群主+班委，
 * 加入链路是"任何登录用户"。混在一个类里，权限校验会很难一眼看全。
 */
public interface ContainerService {

    ContainerCreateVO create(Long userId, ContainerCreateDTO dto);

    ContainerListVO list(Long userId, String tab, String keyword, Long classId, Integer inviteStatus);

    ContainerDetailVO detail(Long userId, Long containerId);

    void update(Long userId, Long containerId, ContainerUpdateDTO dto);

    void dissolve(Long userId, Long containerId, DissolveDTO dto);

    InviteVO invite(Long userId, Long containerId);

    void updateExpire(Long userId, Long containerId, InviteExpireDTO dto);

    /** 重新生成邀请码，返回新码。旧码立即失效 */
    String regenerateCode(Long userId, Long containerId);

    void setJoinPassword(Long userId, Long containerId, JoinPasswordDTO dto);

    List<ContainerLogVO> logs(Long userId, Long containerId, Integer page, Integer size);
}
