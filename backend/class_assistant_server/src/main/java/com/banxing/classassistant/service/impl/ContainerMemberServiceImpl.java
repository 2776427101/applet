package com.banxing.classassistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.banxing.classassistant.common.BizException;
import com.banxing.classassistant.dto.BlacklistVO;
import com.banxing.classassistant.dto.HighlightCreateDTO;
import com.banxing.classassistant.dto.HighlightVO;
import com.banxing.classassistant.dto.MemberListVO;
import com.banxing.classassistant.dto.MemberMuteDTO;
import com.banxing.classassistant.dto.MemberRemoveDTO;
import com.banxing.classassistant.dto.MemberRoleDTO;
import com.banxing.classassistant.dto.MemberVO;
import com.banxing.classassistant.entity.ContainerBlacklist;
import com.banxing.classassistant.entity.ContainerHighlight;
import com.banxing.classassistant.entity.ContainerMember;
import com.banxing.classassistant.entity.ContainerOpLog;
import com.banxing.classassistant.entity.User;
import com.banxing.classassistant.mapper.ContainerBlacklistMapper;
import com.banxing.classassistant.mapper.ContainerHighlightMapper;
import com.banxing.classassistant.mapper.ContainerMemberMapper;
import com.banxing.classassistant.mapper.UserMapper;
import com.banxing.classassistant.service.ContainerAuthService;
import com.banxing.classassistant.service.ContainerLogService;
import com.banxing.classassistant.service.ContainerMemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * 成员管理实现。
 *
 * <p>这里每一处权限判断都在注释里写清了"谁能做、为什么"，
 * 因为这是整个功能里最容易出现越权的地方（班委不能踢人、班委不能动班委）。
 */
@Service
@RequiredArgsConstructor
public class ContainerMemberServiceImpl implements ContainerMemberService {

    /** 禁言上限 30 天；更久请用永久（-1） */
    private static final int MUTE_MAX_MINUTES = 30 * 24 * 60;

    /** 精华列表一次最多返回多少条：精华是"要点"，不是流水，100 条足够 */
    private static final int HIGHLIGHT_LIMIT = 100;

    private final ContainerMemberMapper memberMapper;
    private final ContainerBlacklistMapper blacklistMapper;
    private final ContainerHighlightMapper highlightMapper;
    private final UserMapper userMapper;
    private final ContainerAuthService auth;
    private final ContainerLogService opLog;

    // ---------------------------------------------------------------
    // 成员列表
    // ---------------------------------------------------------------

    /**
     * 成员列表。<b>任何在群成员都能看</b>（和微信群一致：群成员列表不是敏感信息）。
     *
     * <p>关键词过滤放在 Java 里做而不是 SQL：展示名是"群昵称 &gt; 姓名"算出来的，
     * 在 SQL 里要 COALESCE 两个字段，还得 join t_user；成员最多几百人，
     * 全量捞回来再过滤更简单，也不会写错 OR 的括号。
     */
    @Override
    public MemberListVO members(Long userId, Long containerId, Integer role, String keyword, Integer status) {
        auth.requireMember(userId, containerId);

        LambdaQueryWrapper<ContainerMember> qw = new LambdaQueryWrapper<ContainerMember>()
                .eq(ContainerMember::getContainerId, containerId);
        if (role != null) {
            qw.eq(ContainerMember::getRole, role);
        }
        // status 不传默认只看在群；传 -1 看全部（含已移除/已退出）
        if (status == null) {
            qw.eq(ContainerMember::getStatus, ContainerMember.STATUS_IN);
        } else if (status != -1) {
            qw.eq(ContainerMember::getStatus, status);
        }
        qw.orderByAsc(ContainerMember::getRole).orderByAsc(ContainerMember::getJoinedAt);

        List<ContainerMember> rows = memberMapper.selectList(qw);
        Map<Long, User> users = loadUsers(rows.stream().map(ContainerMember::getUserId).toList());

        String k = StringUtils.hasText(keyword) ? keyword.trim().toLowerCase() : null;
        List<MemberVO> list = new ArrayList<>(rows.size());
        for (ContainerMember m : rows) {
            User u = users.get(m.getUserId());
            String display = auth.displayName(m.getNickname(), u);
            if (k != null && !display.toLowerCase().contains(k)) {
                continue;
            }
            list.add(toVO(m, u, display, userId));
        }

        int activeCount = memberMapper.selectCount(new LambdaQueryWrapper<ContainerMember>()
                .eq(ContainerMember::getContainerId, containerId)
                .eq(ContainerMember::getStatus, ContainerMember.STATUS_IN)).intValue();
        return new MemberListVO(list.size(), activeCount, list);
    }

    private MemberVO toVO(ContainerMember m, User u, String display, Long meId) {
        MemberVO vo = new MemberVO();
        vo.setUserId(m.getUserId());
        vo.setName(u == null ? null : u.getName());
        vo.setAvatar(u == null ? null : u.getAvatar());
        vo.setNickname(m.getNickname());
        vo.setDisplayName(display);
        vo.setRole(m.getRole());
        vo.setStatus(m.getStatus());
        vo.setJoinedAt(m.getJoinedAt());
        vo.setMuted(m.isMuted());
        // 禁言已过期就不返回到期时间和原因，否则前端会显示"已禁言至 昨天"
        vo.setMuteUntil(m.isMuted() ? m.getMuteUntil() : null);
        vo.setMuteReason(m.isMuted() ? m.getMuteReason() : null);
        vo.setSelf(Objects.equals(m.getUserId(), meId));
        return vo;
    }

    private Map<Long, User> loadUsers(List<Long> userIds) {
        List<Long> ids = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, User> out = new HashMap<>();
        for (User u : userMapper.selectBatchIds(ids)) {
            out.put(u.getId(), u);
        }
        return out;
    }

    // ---------------------------------------------------------------
    // 设 / 撤班委
    // ---------------------------------------------------------------

    /**
     * 设置或取消班委。<b>只有群主能做</b>，班委不能给别的班委"升职"。
     *
     * <p>不能把角色改成 1（群主）：本期不支持转让，DTO 上已经用 @Max(3) 拦住，
     * 这里再判一次是因为 DTO 校验只对带 @Valid 的 Controller 生效，
     * 万一将来有别的调用路径，不能指望它。
     */
    @Override
    public void setRole(Long userId, Long containerId, Long targetUserId, MemberRoleDTO dto) {
        auth.requireOwner(userId, containerId);
        ContainerMember target = requireInGroupMember(containerId, targetUserId);

        if (Objects.equals(target.getUserId(), userId)) {
            throw new BizException(1001, "不能修改自己的角色");
        }
        if (target.getRole() == ContainerMember.ROLE_OWNER) {
            throw new BizException(1001, "不能修改群主的角色");
        }
        if (dto.getRole() == ContainerMember.ROLE_OWNER) {
            throw new BizException(1001, "本期不支持转让群主");
        }
        if (Objects.equals(target.getRole(), dto.getRole())) {
            return;
        }

        memberMapper.update(null, new LambdaUpdateWrapper<ContainerMember>()
                .eq(ContainerMember::getId, target.getId())
                .eq(ContainerMember::getContainerId, containerId)
                .eq(ContainerMember::getStatus, ContainerMember.STATUS_IN)
                .set(ContainerMember::getRole, dto.getRole()));

        boolean toAdmin = dto.getRole() == ContainerMember.ROLE_ADMIN;
        opLog.write(containerId, userId, targetUserId,
                toAdmin ? ContainerOpLog.SET_ADMIN : ContainerOpLog.UNSET_ADMIN,
                toAdmin ? "设为班委" : "取消班委");
    }

    // ---------------------------------------------------------------
    // 禁言 / 解除禁言
    // ---------------------------------------------------------------

    /**
     * 禁言。<b>群主和班委都能做</b>，但班委只能禁言普通成员：
     * 不能让班委去禁言另一个班委（班委之间互相压制），更不能动群主。
     *
     * <p>禁言的语义是"不能发言"，不是"不能看"：
     * 成员仍然可以查看容器内容、成员列表和精华。
     * 本期没有聊天模块，判定逻辑落在 {@link ContainerAuthService#assertCanSpeak}。
     */
    @Override
    public void mute(Long userId, Long containerId, Long targetUserId, MemberMuteDTO dto) {
        ContainerMember operator = auth.requireAdminOrOwner(userId, containerId);
        ContainerMember target = requireInGroupMember(containerId, targetUserId);

        if (Objects.equals(target.getUserId(), userId)) {
            throw new BizException(1001, "不能禁言自己");
        }
        if (target.getRole() == ContainerMember.ROLE_OWNER) {
            throw new BizException(1001, "不能禁言群主");
        }
        if (operator.getRole() == ContainerMember.ROLE_ADMIN
                && target.getRole() == ContainerMember.ROLE_ADMIN) {
            throw new BizException(3002, "班委不能禁言其他班委");
        }

        Integer minutes = dto.getMuteMinutes();
        boolean unmute = minutes == null || minutes == 0;

        if (unmute) {
            // 解除时四个字段一起清空。用 updateById 是清不掉的（会跳过 null），
            // 必须显式 set(null)，否则页面上"已解除"，库里还留着到期时间
            memberMapper.update(null, new LambdaUpdateWrapper<ContainerMember>()
                    .eq(ContainerMember::getId, target.getId())
                    .set(ContainerMember::getMuteUntil, null)
                    .set(ContainerMember::getMuteReason, null)
                    .set(ContainerMember::getMuteBy, null)
                    .set(ContainerMember::getMuteAt, null));
            opLog.write(containerId, userId, targetUserId, ContainerOpLog.UNMUTE, "解除禁言");
            return;
        }

        if (!StringUtils.hasText(dto.getReason())) {
            throw new BizException(1001, "请填写禁言原因");
        }

        LocalDateTime until;
        String label;
        if (minutes == -1) {
            until = ContainerMember.MUTE_FOREVER;
            label = "永久禁言";
        } else {
            if (minutes < -1) {
                throw new BizException(1001, "禁言时长不合法");
            }
            if (minutes > MUTE_MAX_MINUTES) {
                throw new BizException(1001, "单次禁言最长 30 天，需要更久请选永久");
            }
            until = LocalDateTime.now().plusMinutes(minutes);
            label = "禁言 " + minutes + " 分钟";
        }

        String reason = dto.getReason().trim();
        memberMapper.update(null, new LambdaUpdateWrapper<ContainerMember>()
                .eq(ContainerMember::getId, target.getId())
                .eq(ContainerMember::getStatus, ContainerMember.STATUS_IN)
                .set(ContainerMember::getMuteUntil, until)
                .set(ContainerMember::getMuteReason, reason)
                .set(ContainerMember::getMuteBy, userId)
                .set(ContainerMember::getMuteAt, LocalDateTime.now()));

        opLog.write(containerId, userId, targetUserId, ContainerOpLog.MUTE, label + "：" + reason);
    }

    // ---------------------------------------------------------------
    // 移除成员（可同时拉黑）
    // ---------------------------------------------------------------

    /**
     * 移除成员。<b>只有群主能做</b> —— 这是本次需求明确划的线：
     * 班委可以禁言，但不能把人踢出去。
     *
     * <p>移除和"解散容器"是两件事：移除只是这个人不在群里了，容器还在，其他人照常用。
     * 前端文案必须区分开，否则老师会以为点一下就解散了。
     *
     * <p>{@code blacklist=true} 时同时写黑名单。不拉黑的话，这个人拿着邀请码
     * 还能再进来（{@code status} 从 2 恢复成 1）—— 这是"先请出去、允许改过"的场景。
     */
    @Override
    @Transactional
    public void remove(Long userId, Long containerId, Long targetUserId, MemberRemoveDTO dto) {
        auth.requireOwner(userId, containerId);
        ContainerMember target = requireInGroupMember(containerId, targetUserId);

        if (Objects.equals(target.getUserId(), userId)) {
            throw new BizException(1001, "不能移除自己");
        }
        if (target.getRole() == ContainerMember.ROLE_OWNER) {
            throw new BizException(1001, "不能移除群主");
        }

        // 被移除的管理员要顺带降级：留着 role=2 会让"曾经的管理员"在历史数据里
        // 看起来还有管理权限，将来做权限回溯时会误判
        memberMapper.update(null, new LambdaUpdateWrapper<ContainerMember>()
                .eq(ContainerMember::getId, target.getId())
                .eq(ContainerMember::getContainerId, containerId)
                .eq(ContainerMember::getStatus, ContainerMember.STATUS_IN)
                .set(ContainerMember::getStatus, ContainerMember.STATUS_REMOVED)
                .set(ContainerMember::getRole, ContainerMember.ROLE_MEMBER)
                .set(ContainerMember::getRemovedBy, userId)
                .set(ContainerMember::getRemovedAt, LocalDateTime.now())
                .set(ContainerMember::getMuteUntil, null)
                .set(ContainerMember::getMuteReason, null)
                .set(ContainerMember::getMuteBy, null)
                .set(ContainerMember::getMuteAt, null));

        // 移除接口的参数走 query（见 ContainerController 的注释），没有经过 @Valid，
        // 所以长度要在这里兜一下：超长会撞 VARCHAR(100) 直接 5000，那是接口的错不是用户的错
        String reason = clip(dto.getReason(), 100);
        opLog.write(containerId, userId, targetUserId, ContainerOpLog.REMOVE,
                "移出容器" + (reason == null ? "" : "：" + reason));

        if (Boolean.TRUE.equals(dto.getBlacklist())) {
            upsertBlacklist(containerId, targetUserId, userId, reason);
            opLog.write(containerId, userId, targetUserId, ContainerOpLog.BLACKLIST,
                    "加入黑名单" + (reason == null ? "" : "：" + reason));
        }
    }

    /**
     * 写黑名单。已经在黑名单里（含已释放的旧记录）就更新原行。
     *
     * <p>为什么是 UPDATE 而不是 INSERT：表上有 (container_id, user_id) 唯一键，
     * 一个人被拉黑 → 释放 → 再拉黑，如果直接 INSERT 会撞键报 500。
     * 复用同一行也更符合"这个人和这个群的关系"这个语义。
     */
    private void upsertBlacklist(Long containerId, Long targetUserId, Long operatorId, String reason) {
        ContainerBlacklist exist = blacklistMapper.selectOne(new LambdaQueryWrapper<ContainerBlacklist>()
                .eq(ContainerBlacklist::getContainerId, containerId)
                .eq(ContainerBlacklist::getUserId, targetUserId));
        if (exist == null) {
            ContainerBlacklist row = new ContainerBlacklist();
            row.setContainerId(containerId);
            row.setUserId(targetUserId);
            row.setReason(reason);
            row.setOperatorId(operatorId);
            blacklistMapper.insert(row);
        } else {
            blacklistMapper.update(null, new LambdaUpdateWrapper<ContainerBlacklist>()
                    .eq(ContainerBlacklist::getId, exist.getId())
                    .set(ContainerBlacklist::getReason, reason)
                    .set(ContainerBlacklist::getOperatorId, operatorId)
                    .set(ContainerBlacklist::getCreatedAt, LocalDateTime.now())
                    .set(ContainerBlacklist::getReleasedBy, null)
                    .set(ContainerBlacklist::getReleasedAt, null));
        }
    }

    // ---------------------------------------------------------------
    // 黑名单
    // ---------------------------------------------------------------

    /**
     * 黑名单队列。<b>只有群主能看</b>：被谁拉黑、为什么拉黑，
     * 让班委看到会让"管纪律的人"变成"被记恨的人"。
     */
    @Override
    public List<BlacklistVO> blacklist(Long userId, Long containerId) {
        auth.requireOwner(userId, containerId);

        List<ContainerBlacklist> rows = blacklistMapper.selectList(new LambdaQueryWrapper<ContainerBlacklist>()
                .eq(ContainerBlacklist::getContainerId, containerId)
                .isNull(ContainerBlacklist::getReleasedAt)
                .orderByDesc(ContainerBlacklist::getCreatedAt));
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, User> users = loadUsers(rows.stream()
                .flatMap(r -> Stream.of(r.getUserId(), r.getOperatorId()))
                .filter(Objects::nonNull)
                .distinct()
                .toList());

        List<BlacklistVO> out = new ArrayList<>(rows.size());
        for (ContainerBlacklist r : rows) {
            User u = users.get(r.getUserId());
            User op = users.get(r.getOperatorId());
            BlacklistVO vo = new BlacklistVO();
            vo.setUserId(r.getUserId());
            vo.setName(u == null ? null : u.getName());
            vo.setDisplayName(auth.displayName(null, u));
            vo.setReason(r.getReason());
            vo.setOperatorName(op == null ? "已注销用户" : auth.displayName(null, op));
            vo.setCreatedAt(r.getCreatedAt());
            out.add(vo);
        }
        return out;
    }

    /**
     * 解除拉黑。只把 {@code released_at} 写上，不删行 —— 留档。
     *
     * <p>解除之后这个人<b>还需要自己重新扫码加入</b>，不会自动回到群里。
     * 这是有意的：老师放出来不代表欢迎回来，只是"不再禁止"。
     */
    @Override
    public void releaseBlacklist(Long userId, Long containerId, Long targetUserId) {
        auth.requireOwner(userId, containerId);

        ContainerBlacklist row = blacklistMapper.selectOne(new LambdaQueryWrapper<ContainerBlacklist>()
                .eq(ContainerBlacklist::getContainerId, containerId)
                .eq(ContainerBlacklist::getUserId, targetUserId));
        if (row == null || row.getReleasedAt() != null) {
            throw new BizException(1004, "该用户不在黑名单中");
        }

        blacklistMapper.update(null, new LambdaUpdateWrapper<ContainerBlacklist>()
                .eq(ContainerBlacklist::getId, row.getId())
                .set(ContainerBlacklist::getReleasedBy, userId)
                .set(ContainerBlacklist::getReleasedAt, LocalDateTime.now()));

        opLog.write(containerId, userId, targetUserId, ContainerOpLog.RELEASE_BLACKLIST, "解除黑名单");
    }

    // ---------------------------------------------------------------
    // 精华消息
    // ---------------------------------------------------------------

    /** 精华列表：任何在群成员都能看（这就是"精华"的意义） */
    @Override
    public List<HighlightVO> highlights(Long userId, Long containerId) {
        auth.requireMember(userId, containerId);

        LambdaQueryWrapper<ContainerHighlight> qw = new LambdaQueryWrapper<ContainerHighlight>()
                .eq(ContainerHighlight::getContainerId, containerId)
                .eq(ContainerHighlight::getStatus, 1)
                .orderByDesc(ContainerHighlight::getCreatedAt)
                .last("LIMIT " + HIGHLIGHT_LIMIT);
        List<ContainerHighlight> rows = highlightMapper.selectList(qw);
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, User> users = loadUsers(rows.stream()
                .map(ContainerHighlight::getCreatorId)
                .filter(Objects::nonNull)
                .distinct()
                .toList());

        List<HighlightVO> out = new ArrayList<>(rows.size());
        for (ContainerHighlight r : rows) {
            User u = users.get(r.getCreatorId());
            HighlightVO vo = new HighlightVO();
            vo.setId(r.getId());
            vo.setContent(r.getContent());
            vo.setCreatorId(r.getCreatorId());
            vo.setCreatorName(u == null ? "已注销用户" : auth.displayName(null, u));
            vo.setCreatedAt(r.getCreatedAt());
            vo.setMine(Objects.equals(r.getCreatorId(), userId));
            vo.setMessageId(r.getMessageId());
            out.add(vo);
        }
        return out;
    }

    /**
     * 收录精华。<b>群主和班委都能做</b> —— 这正是"班委"这个角色存在的意义：
     * 学习委员把重点整理成精华，老师不用自己一条条翻。
     */
    @Override
    public HighlightVO addHighlight(Long userId, Long containerId, HighlightCreateDTO dto) {
        auth.requireAdminOrOwner(userId, containerId);

        ContainerHighlight row = new ContainerHighlight();
        row.setContainerId(containerId);
        row.setMessageId(dto.getMessageId());
        row.setContent(dto.getContent().trim());
        row.setCreatorId(userId);
        row.setStatus(1);
        highlightMapper.insert(row);

        String preview = row.getContent().length() > 30 ? row.getContent().substring(0, 30) + "…" : row.getContent();
        opLog.write(containerId, userId, null, ContainerOpLog.HIGHLIGHT, "收录精华：" + preview);

        User u = userMapper.selectById(userId);
        HighlightVO vo = new HighlightVO();
        vo.setId(row.getId());
        vo.setContent(row.getContent());
        vo.setCreatorId(userId);
        vo.setCreatorName(auth.displayName(null, u));
        vo.setCreatedAt(LocalDateTime.now());
        vo.setMine(true);
        vo.setMessageId(row.getMessageId());
        return vo;
    }

    /**
     * 取消精华。群主可以取消任何一条，班委只能取消自己收录的 ——
     * 否则班委会互相删对方的整理成果。
     */
    @Override
    public void cancelHighlight(Long userId, Long containerId, Long highlightId) {
        ContainerMember operator = auth.requireAdminOrOwner(userId, containerId);

        ContainerHighlight row = highlightMapper.selectOne(new LambdaQueryWrapper<ContainerHighlight>()
                .eq(ContainerHighlight::getId, highlightId)
                .eq(ContainerHighlight::getContainerId, containerId)
                .eq(ContainerHighlight::getStatus, 1));
        if (row == null) {
            throw new BizException(1004, "精华消息不存在或已取消");
        }
        if (operator.getRole() == ContainerMember.ROLE_ADMIN
                && !Objects.equals(row.getCreatorId(), userId)) {
            throw new BizException(3002, "班委只能取消自己收录的精华");
        }

        highlightMapper.update(null, new LambdaUpdateWrapper<ContainerHighlight>()
                .eq(ContainerHighlight::getId, row.getId())
                .set(ContainerHighlight::getStatus, 0)
                .set(ContainerHighlight::getCanceledBy, userId)
                .set(ContainerHighlight::getCanceledAt, LocalDateTime.now()));

        opLog.write(containerId, userId, row.getCreatorId(), ContainerOpLog.CANCEL_HIGHLIGHT, "取消精华");
    }

    // ---------------------------------------------------------------
    // 公共
    // ---------------------------------------------------------------

    /**
     * 取一个"在群"的成员行，不在就抛 1004。
     *
     * <p>用 1004 而不是 3002：这里的语义是"你要操作的那个人不在群里"，
     * 不是"你没权限"。混用会让前端在成员列表里把这个人误判成"已被移出"。
     */
    private ContainerMember requireInGroupMember(Long containerId, Long targetUserId) {
        if (targetUserId == null) {
            throw new BizException(1001, "成员 id 不能为空");
        }
        ContainerMember m = auth.findMember(containerId, targetUserId);
        if (m == null || m.getStatus() == null || m.getStatus() != ContainerMember.STATUS_IN) {
            throw new BizException(1004, "该成员不在容器中");
        }
        return m;
    }

    /** 去空格 + 截断到指定长度；空串归一成 null */
    private String clip(String s, int max) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
