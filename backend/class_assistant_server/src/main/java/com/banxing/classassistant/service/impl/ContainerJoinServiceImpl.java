package com.banxing.classassistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.banxing.classassistant.common.BizException;
import com.banxing.classassistant.dto.JoinDTO;
import com.banxing.classassistant.dto.JoinPreviewVO;
import com.banxing.classassistant.dto.JoinResultVO;
import com.banxing.classassistant.entity.ClassInfo;
import com.banxing.classassistant.entity.ContainerBlacklist;
import com.banxing.classassistant.entity.ContainerJoinAttempt;
import com.banxing.classassistant.entity.ContainerMember;
import com.banxing.classassistant.entity.SubjectContainer;
import com.banxing.classassistant.mapper.ClassMapper;
import com.banxing.classassistant.mapper.ContainerBlacklistMapper;
import com.banxing.classassistant.mapper.ContainerJoinAttemptMapper;
import com.banxing.classassistant.mapper.ContainerMemberMapper;
import com.banxing.classassistant.mapper.SubjectContainerMapper;
import com.banxing.classassistant.service.ContainerAuthService;
import com.banxing.classassistant.service.ContainerJoinService;
import com.banxing.classassistant.util.InviteCodeGenerator;
import com.banxing.classassistant.util.PasswordHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 加入链路实现。
 *
 * <p><b>校验顺序就是安全设计</b>，改顺序之前先想清楚：
 * <ol>
 *   <li>码格式 —— 少输一位不该查库</li>
 *   <li>码是否存在、容器是否已解散 —— 1008</li>
 *   <li>是否过期 —— 1008</li>
 *   <li>是否在黑名单 —— 3003（比"已加入"更优先：被拉黑的人不该看到"你已在群里"）</li>
 *   <li>是否已是成员 —— 1006（幂等，前端当成功处理）</li>
 *   <li>密码 —— 1005 / 1007</li>
 *   <li>写成员行</li>
 * </ol>
 * 密码放在成员判断之后，是为了让"已经是成员的人"不用再输一次密码。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContainerJoinServiceImpl implements ContainerJoinService {

    /** 连续错 5 次锁 15 分钟 */
    private static final int MAX_FAIL = 5;
    private static final int LOCK_MINUTES = 15;

    private final SubjectContainerMapper containerMapper;
    private final ContainerMemberMapper memberMapper;
    private final ContainerBlacklistMapper blacklistMapper;
    private final ContainerJoinAttemptMapper attemptMapper;
    private final ClassMapper classMapper;
    private final ContainerAuthService auth;

    // ---------------------------------------------------------------
    // 预览
    // ---------------------------------------------------------------

    /**
     * 输完码先看一眼"这是哪个群、要不要密码"。
     *
     * <p>过期和黑名单都<b>不抛异常</b>，而是作为字段返回：
     * 用户此刻最需要看到的是容器名，直接弹个 toast 再把人踢回输入页体验很差。
     * 真正的拦截在 {@link #join} 里。
     */
    @Override
    public JoinPreviewVO preview(Long userId, String rawCode) {
        SubjectContainer c = requireByCode(rawCode);

        JoinPreviewVO vo = new JoinPreviewVO();
        vo.setContainerId(c.getId());
        vo.setName(c.getName());
        vo.setSubjectName(c.getSubjectName());
        vo.setMemberCount(memberMapper.selectCount(new LambdaQueryWrapper<ContainerMember>()
                .eq(ContainerMember::getContainerId, c.getId())
                .eq(ContainerMember::getStatus, ContainerMember.STATUS_IN)).intValue());
        vo.setNeedPassword(c.getJoinPasswordHash() != null);
        vo.setExpired(auth.isInviteExpired(c));

        ContainerMember me = auth.findMember(c.getId(), userId);
        vo.setMyRole(me != null && me.getStatus() != null && me.getStatus() == ContainerMember.STATUS_IN
                ? me.getRole() : 0);
        vo.setBlacklisted(isBlacklisted(c.getId(), userId));

        if (c.getClassId() != null) {
            ClassInfo clazz = classMapper.selectById(c.getClassId());
            vo.setClassName(clazz == null ? null : clazz.getName());
        }
        return vo;
    }

    // ---------------------------------------------------------------
    // 加入
    // ---------------------------------------------------------------

    /**
     * 加入容器。
     *
     * <p><b>这里故意不加 {@code @Transactional}</b>，这是一个踩过的坑：
     * 密码错误时要"写入失败次数 + 抛 1007"，如果整个方法在一个事务里，
     * 抛异常会把刚写的失败计数一起回滚 —— 数据库里永远只有 0 次，
     * 锁定机制看起来存在、实际完全失效（实测：连错 5 次仍然提示"还可尝试 4 次"）。
     *
     * <p>不加事务是安全的：这里只有"写尝试记录"和"写成员行"两个单条语句，
     * 各自本身就是原子的；即使第二条失败，第一条已提交的也只是一次计数重置，不影响正确性。
     */
    @Override
    public JoinResultVO join(Long userId, JoinDTO dto) {
        SubjectContainer c = requireByCode(dto.getCode());

        if (auth.isInviteExpired(c)) {
            throw new BizException(1008, "邀请码已过期，请联系老师重新获取");
        }
        if (isBlacklisted(c.getId(), userId)) {
            throw new BizException(3003, "你已被移出该容器，无法再次加入");
        }

        ContainerMember exist = auth.findMember(c.getId(), userId);
        if (exist != null && exist.getStatus() != null && exist.getStatus() == ContainerMember.STATUS_IN) {
            // 幂等：重复点"加入"不该报错。前端把它当成功直接跳详情页
            throw new BizException(1006, "你已在该容器中");
        }

        if (c.getJoinPasswordHash() != null) {
            checkPassword(c, userId, dto.getPassword());
        }

        if (exist == null) {
            ContainerMember m = new ContainerMember();
            m.setContainerId(c.getId());
            m.setUserId(userId);
            m.setRole(ContainerMember.ROLE_MEMBER);
            m.setStatus(ContainerMember.STATUS_IN);
            m.setJoinSource(1);
            m.setJoinedAt(LocalDateTime.now());
            try {
                memberMapper.insert(m);
            } catch (DuplicateKeyException e) {
                // 同一用户并发点两次，第二次撞 uk_container_user。
                // 这不是错误，是"已经加进去了"
                throw new BizException(1006, "你已在该容器中");
            }
        } else {
            // 曾经退出（status=3）或被移除（status=2）但没被拉黑 → 恢复成正常成员。
            // 被移除的人如果没进黑名单，是可以凭码回来的 —— 这正是"拉黑"这个开关的意义
            memberMapper.update(null, new LambdaUpdateWrapper<ContainerMember>()
                    .eq(ContainerMember::getId, exist.getId())
                    .set(ContainerMember::getRole, ContainerMember.ROLE_MEMBER)
                    .set(ContainerMember::getStatus, ContainerMember.STATUS_IN)
                    .set(ContainerMember::getJoinSource, 1)
                    .set(ContainerMember::getJoinedAt, LocalDateTime.now())
                    .set(ContainerMember::getRemovedBy, null)
                    .set(ContainerMember::getRemovedAt, null)
                    .set(ContainerMember::getMuteUntil, null)
                    .set(ContainerMember::getMuteReason, null)
                    .set(ContainerMember::getMuteBy, null)
                    .set(ContainerMember::getMuteAt, null));
        }

        return new JoinResultVO(c.getId(), c.getName(), ContainerMember.ROLE_MEMBER);
    }

    // ---------------------------------------------------------------
    // 退出
    // ---------------------------------------------------------------

    /**
     * 主动退出。
     *
     * <p>群主不能退出：群主一走容器就没人管了，也没人能解散它。
     * 想不干了请先解散容器（本期不支持转让群主）。
     */
    @Override
    public void leave(Long userId, Long containerId) {
        ContainerMember m = auth.requireMember(userId, containerId);
        if (m.getRole() != null && m.getRole() == ContainerMember.ROLE_OWNER) {
            throw new BizException(1001, "群主不能退出，请先解散容器");
        }

        memberMapper.update(null, new LambdaUpdateWrapper<ContainerMember>()
                .eq(ContainerMember::getId, m.getId())
                .eq(ContainerMember::getStatus, ContainerMember.STATUS_IN)
                .set(ContainerMember::getStatus, ContainerMember.STATUS_LEFT)
                .set(ContainerMember::getMuteUntil, null)
                .set(ContainerMember::getMuteReason, null)
                .set(ContainerMember::getMuteBy, null)
                .set(ContainerMember::getMuteAt, null));
    }

    // ---------------------------------------------------------------
    // 内部
    // ---------------------------------------------------------------

    /**
     * 规范化 + 格式校验 + 查容器。三种失败用不同错误码，前端文案才能说清楚。
     */
    private SubjectContainer requireByCode(String rawCode) {
        String code = InviteCodeGenerator.normalize(rawCode);
        if (!InviteCodeGenerator.isValidFormat(code)) {
            throw new BizException(1001, "邀请码格式不正确，应为 10 位字母或数字");
        }
        SubjectContainer c = containerMapper.selectOne(new LambdaQueryWrapper<SubjectContainer>()
                .eq(SubjectContainer::getInviteCode, code));
        if (c == null || c.getStatus() == null || c.getStatus() != 1) {
            // 已解散和"码不存在"合并成一句话：避免有人拿码来探测哪些容器存在过
            throw new BizException(1008, "邀请码无效或容器已解散");
        }
        return c;
    }

    private boolean isBlacklisted(Long containerId, Long userId) {
        Long n = blacklistMapper.selectCount(new LambdaQueryWrapper<ContainerBlacklist>()
                .eq(ContainerBlacklist::getContainerId, containerId)
                .eq(ContainerBlacklist::getUserId, userId)
                .isNull(ContainerBlacklist::getReleasedAt));
        return n != null && n > 0;
    }

    /**
     * 校验加入密码，同时维护失败计数。
     *
     * <p>锁定判断必须在密码比对之前：否则攻击者在锁定期内继续尝试，
     * 每次都能拿到"密码错误"的快速响应，锁定形同虚设。
     */
    private void checkPassword(SubjectContainer c, Long userId, String raw) {
        LocalDateTime now = LocalDateTime.now();
        ContainerJoinAttempt a = attemptMapper.selectOne(new LambdaQueryWrapper<ContainerJoinAttempt>()
                .eq(ContainerJoinAttempt::getContainerId, c.getId())
                .eq(ContainerJoinAttempt::getUserId, userId));

        if (a != null && a.getLockedUntil() != null && a.getLockedUntil().isAfter(now)) {
            long mins = Math.max(1, Duration.between(now, a.getLockedUntil()).toMinutes() + 1);
            throw new BizException(1005, "密码错误次数过多，请 " + mins + " 分钟后再试");
        }

        if (!StringUtils.hasText(raw)) {
            throw new BizException(1001, "请输入加入密码");
        }

        if (PasswordHasher.matches(raw, c.getJoinPasswordHash())) {
            if (a != null) {
                clearAttempt(a);
            }
            return;
        }

        // 锁已过期 → 计数从 0 重新开始，否则解锁后错一次又立刻被锁
        boolean lockExpired = a != null && a.getLockedUntil() != null && !a.getLockedUntil().isAfter(now);
        int prev = (a == null || lockExpired) ? 0 : (a.getFailCount() == null ? 0 : a.getFailCount());
        int fail = prev + 1;

        if (fail >= MAX_FAIL) {
            saveAttempt(a, c.getId(), userId, fail, now.plusMinutes(LOCK_MINUTES));
            throw new BizException(1005, "密码错误次数过多，请 " + LOCK_MINUTES + " 分钟后再试");
        }
        saveAttempt(a, c.getId(), userId, fail, null);
        throw new BizException(1007, "加入密码不正确，还可尝试 " + (MAX_FAIL - fail) + " 次");
    }

    /**
     * 写失败计数。第一次 insert，之后 update。
     *
     * <p>insert 撞唯一键说明另一个并发请求刚插进去，改成 update 即可 ——
     * 这里丢一次计数不影响安全（最多让攻击者多试一次），但抛 500 会让人以为接口坏了。
     */
    private void saveAttempt(ContainerJoinAttempt a, Long containerId, Long userId,
                             int failCount, LocalDateTime lockedUntil) {
        if (a == null) {
            ContainerJoinAttempt row = new ContainerJoinAttempt();
            row.setContainerId(containerId);
            row.setUserId(userId);
            row.setFailCount(failCount);
            row.setLockedUntil(lockedUntil);
            try {
                attemptMapper.insert(row);
                return;
            } catch (DuplicateKeyException e) {
                log.debug("加入尝试记录并发插入，改为更新: container={}, user={}", containerId, userId);
                a = attemptMapper.selectOne(new LambdaQueryWrapper<ContainerJoinAttempt>()
                        .eq(ContainerJoinAttempt::getContainerId, containerId)
                        .eq(ContainerJoinAttempt::getUserId, userId));
                if (a == null) {
                    return;
                }
            }
        }
        attemptMapper.update(null, new LambdaUpdateWrapper<ContainerJoinAttempt>()
                .eq(ContainerJoinAttempt::getId, a.getId())
                .set(ContainerJoinAttempt::getFailCount, failCount)
                .set(ContainerJoinAttempt::getLockedUntil, lockedUntil));
    }

    private void clearAttempt(ContainerJoinAttempt a) {
        attemptMapper.update(null, new LambdaUpdateWrapper<ContainerJoinAttempt>()
                .eq(ContainerJoinAttempt::getId, a.getId())
                .set(ContainerJoinAttempt::getFailCount, 0)
                .set(ContainerJoinAttempt::getLockedUntil, null));
    }
}
