package com.banxing.classassistant.service;

import com.banxing.classassistant.dto.BlacklistVO;
import com.banxing.classassistant.dto.HighlightCreateDTO;
import com.banxing.classassistant.dto.HighlightVO;
import com.banxing.classassistant.dto.MemberListVO;
import com.banxing.classassistant.dto.MemberMuteDTO;
import com.banxing.classassistant.dto.MemberRemoveDTO;
import com.banxing.classassistant.dto.MemberRoleDTO;

import java.util.List;

/**
 * 成员管理：成员列表、设/撤班委、禁言、移除、黑名单、精华消息。
 *
 * <p>权限分配（本次需求明确划定）：
 * <ul>
 *   <li>群主（老师）：全部</li>
 *   <li>班委（管理员）：禁言普通成员、收录精华、看邀请码；<b>不能踢人</b>、不能改配置</li>
 *   <li>成员：只能看成员列表和自己的精华</li>
 * </ul>
 */
public interface ContainerMemberService {

    MemberListVO members(Long userId, Long containerId, Integer role, String keyword, Integer status);

    void setRole(Long userId, Long containerId, Long targetUserId, MemberRoleDTO dto);

    void mute(Long userId, Long containerId, Long targetUserId, MemberMuteDTO dto);

    void remove(Long userId, Long containerId, Long targetUserId, MemberRemoveDTO dto);

    List<BlacklistVO> blacklist(Long userId, Long containerId);

    void releaseBlacklist(Long userId, Long containerId, Long targetUserId);

    List<HighlightVO> highlights(Long userId, Long containerId);

    HighlightVO addHighlight(Long userId, Long containerId, HighlightCreateDTO dto);

    void cancelHighlight(Long userId, Long containerId, Long highlightId);
}
