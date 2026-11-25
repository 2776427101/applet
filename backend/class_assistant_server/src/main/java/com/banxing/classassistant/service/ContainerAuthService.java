package com.banxing.classassistant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.banxing.classassistant.common.BizException;
import com.banxing.classassistant.dto.ContainerDetailVO;
import com.banxing.classassistant.entity.ContainerMember;
import com.banxing.classassistant.entity.SubjectContainer;
import com.banxing.classassistant.entity.User;
import com.banxing.classassistant.mapper.ContainerMemberMapper;
import com.banxing.classassistant.mapper.SubjectContainerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 容器级权限校验的<b>唯一入口</b>。
 *
 * <p>为什么必须有这个类：本项目既有的教师端接口全部"不带 id"，班级从 token 反查，
 * 所以水平越权在结构上就不可能（见 api-contract §0 铁律①）。
 * 容器是第一次出现「教师端带 {id} 的写接口」，一旦谁忘了在某个方法里校验归属，
 * 就是"老师 A 能改老师 B 的容器"。
 *
 * <p>所以约定：<b>容器相关的每个 Service 方法，第一行必须是这里的一个 requireXxx</b>。
 * 和 ClassService.requireTeacher 一样，不写成 interface + impl ——
 * 它不存在第二种实现。
 */
@Service
@RequiredArgsConstructor
public class ContainerAuthService {

    private final SubjectContainerMapper containerMapper;
    private final ContainerMemberMapper memberMapper;

    /**
     * 按 id 取容器，不存在或已解散都抛 1004。
     *
     * <p>解散后对所有人（包括群主）都返回"不存在或已解散"：
     * 前端不该能进一个已解散容器的详情页，也没必要区分这两种情况。
     */
    public SubjectContainer requireContainer(Long containerId) {
        if (containerId == null) {
            throw new BizException(1001, "容器 id 不能为空");
        }
        SubjectContainer c = containerMapper.selectById(containerId);
        if (c == null || c.getStatus() == null || c.getStatus() != 1) {
            throw new BizException(1004, "学科容器不存在或已解散");
        }
        return c;
    }

    /**
     * 当前用户必须是在群成员，返回他的成员行。
     *
     * <p>被移除（status=2）和已退出（status=3）都不算成员 —— 这正是
     * "被移除成员不能继续访问容器内容"的落点，不需要在每个接口里各判一次。
     */
    public ContainerMember requireMember(Long userId, Long containerId) {
        SubjectContainer c = requireContainer(containerId);
        ContainerMember m = findMember(c.getId(), userId);
        if (m == null || m.getStatus() == null || m.getStatus() != ContainerMember.STATUS_IN) {
            throw new BizException(3002, "你不是该学科容器的成员");
        }
        return m;
    }

    /** 群主 */
    public ContainerMember requireOwner(Long userId, Long containerId) {
        ContainerMember m = requireMember(userId, containerId);
        if (m.getRole() != ContainerMember.ROLE_OWNER) {
            throw new BizException(3002, "只有群主可以操作");
        }
        return m;
    }

    /** 群主或班委（管理员） */
    public ContainerMember requireAdminOrOwner(Long userId, Long containerId) {
        ContainerMember m = requireMember(userId, containerId);
        if (m.getRole() != ContainerMember.ROLE_OWNER && m.getRole() != ContainerMember.ROLE_ADMIN) {
            throw new BizException(3002, "只有群主或班委可以操作");
        }
        return m;
    }

    /** 查成员行（可能在群，也可能被移除/已退出），不校验权限 */
    public ContainerMember findMember(Long containerId, Long userId) {
        return memberMapper.selectOne(new LambdaQueryWrapper<ContainerMember>()
                .eq(ContainerMember::getContainerId, containerId)
                .eq(ContainerMember::getUserId, userId));
    }

    /**
     * 当前角色能做什么。只给前端做按钮显隐用，<b>后端一律不读它做判断</b>。
     *
     * <p>班委（管理员）的权限边界是这次需求明确划的：
     * 可以禁言、可以收录精华、可以看邀请码，<b>不能踢人</b>、不能改配置、不能设管理员。
     */
    public ContainerDetailVO.Permissions permissionsOf(Integer role) {
        ContainerDetailVO.Permissions p = new ContainerDetailVO.Permissions();
        int r = role == null ? 0 : role;
        if (r == ContainerMember.ROLE_OWNER) {
            p.setCanEdit(true);
            p.setCanManageAdmin(true);
            p.setCanRemoveMember(true);
            p.setCanMute(true);
            p.setCanHighlight(true);
            p.setCanViewInvite(true);
            p.setCanRegenerateCode(true);
            p.setCanSetPassword(true);
            p.setCanDissolve(true);
            p.setCanViewBlacklist(true);
        } else if (r == ContainerMember.ROLE_ADMIN) {
            p.setCanMute(true);
            p.setCanHighlight(true);
            p.setCanViewInvite(true);
        }
        return p;
    }

    /**
     * 发言前的校验：必须是在群成员，且没有被禁言。
     *
     * <p>这是"被禁言的人不能发言"这条规则的唯一落点 ——
     * 禁言如果只在成员列表里显示一个标签、发消息时却不拦，那禁言就是假的。
     *
     * <p>返回成员行而不是 void：调用方（发消息）马上就要用 role/nickname 组装出参，
     * 返回出来可以省掉一次完全相同的查询。
     */
    public ContainerMember assertCanSpeak(Long containerId, Long userId) {
        ContainerMember m = requireMember(userId, containerId);
        if (m.isMuted()) {
            throw new BizException(3005, "你已被禁言，暂时不能发言");
        }
        return m;
    }

    /** 邀请码是否已过期。永久有效（null）永远返回 false */
    public boolean isInviteExpired(SubjectContainer c) {
        return c.getInviteExpireAt() != null && c.getInviteExpireAt().isBefore(LocalDateTime.now());
    }

    /**
     * 展示名：群昵称 &gt; 真实姓名 &gt; 「未设置姓名」。
     *
     * <p>登录时自动建的账号 name 是空串（AuthServiceImpl 里写死 ""），
     * 直接显示会是空白行，看起来像加载失败。
     */
    public String displayName(String nickname, User user) {
        if (StringUtils.hasText(nickname)) {
            return nickname.trim();
        }
        if (user != null && StringUtils.hasText(user.getName())) {
            return user.getName().trim();
        }
        return "未设置姓名";
    }
}
