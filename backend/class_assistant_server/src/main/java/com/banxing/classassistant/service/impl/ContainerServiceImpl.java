package com.banxing.classassistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.banxing.classassistant.common.BizException;
import com.banxing.classassistant.dto.ContainerCreateDTO;
import com.banxing.classassistant.dto.ContainerCreateVO;
import com.banxing.classassistant.dto.ContainerDetailVO;
import com.banxing.classassistant.dto.ContainerListVO;
import com.banxing.classassistant.dto.ContainerLogVO;
import com.banxing.classassistant.dto.ContainerUpdateDTO;
import com.banxing.classassistant.dto.ContainerVO;
import com.banxing.classassistant.dto.DissolveDTO;
import com.banxing.classassistant.dto.InviteExpireDTO;
import com.banxing.classassistant.dto.InviteVO;
import com.banxing.classassistant.dto.JoinPasswordDTO;
import com.banxing.classassistant.entity.ClassInfo;
import com.banxing.classassistant.entity.ContainerMember;
import com.banxing.classassistant.entity.ContainerOpLog;
import com.banxing.classassistant.entity.SubjectContainer;
import com.banxing.classassistant.entity.Student;
import com.banxing.classassistant.entity.User;
import com.banxing.classassistant.mapper.ClassMapper;
import com.banxing.classassistant.mapper.ContainerMemberMapper;
import com.banxing.classassistant.mapper.ContainerOpLogMapper;
import com.banxing.classassistant.mapper.SubjectContainerMapper;
import com.banxing.classassistant.mapper.StudentMapper;
import com.banxing.classassistant.mapper.UserMapper;
import com.banxing.classassistant.service.ClassService;
import com.banxing.classassistant.service.ContainerAuthService;
import com.banxing.classassistant.service.ContainerLogService;
import com.banxing.classassistant.service.ContainerService;
import com.banxing.classassistant.util.InviteCodeGenerator;
import com.banxing.classassistant.util.PasswordHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 学科容器管理侧实现。
 *
 * <p>每个方法第一行都是权限校验（{@link ContainerAuthService}），没有例外。
 * 这是本项目第一次出现"教师端带 {id}"的接口，越权风险全部压在这几行上。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContainerServiceImpl implements ContainerService {

    /** 邀请码撞唯一键时的重试次数。32^10 的组合下撞一次已经是小概率，5 次足够 */
    private static final int CODE_RETRY = 5;

    private final SubjectContainerMapper containerMapper;
    private final ContainerMemberMapper memberMapper;
    private final ContainerOpLogMapper opLogMapper;
    private final UserMapper userMapper;
    private final ClassMapper classMapper;
    private final StudentMapper studentMapper;
    private final ClassService classService;
    private final ContainerAuthService auth;
    private final ContainerLogService opLog;

    // ---------------------------------------------------------------
    // 创建
    // ---------------------------------------------------------------

    /**
     * 创建容器：生成邀请码 → 插容器 → 插群主成员行，三步在同一个事务里。
     *
     * <p>如果第二步失败但第一步成功，会留下一个没有群主的容器 —— 谁也进不去、也删不掉。
     * 所以这里必须 {@code @Transactional}，和"座位保存"那种"先校验全部再写"的用法不同，
     * 这里是真的需要原子性。
     */
    @Override
    @Transactional
    public ContainerCreateVO create(Long userId, ContainerCreateDTO dto) {
        classService.requireTeacher(userId);

        // 关联行政班：只能关联自己带的班，否则就是"把容器挂到别人班上"
        String className = null;
        if (dto.getClassId() != null) {
            ClassInfo clazz = classMapper.selectById(dto.getClassId());
            if (clazz == null) {
                throw new BizException(1004, "关联的班级不存在");
            }
            if (!Objects.equals(clazz.getHeadTeacherId(), userId)) {
                throw new BizException(3002, "只能关联你自己带的班级");
            }
            className = clazz.getName();
        }

        String subjectName = dto.getSubjectName().trim();
        // 名称与学科已经合并成一个字段：前端两个都传同一个值。
        // 仍然保留「没传 name 就按 学科 · 班级 兜底」的逻辑，是为了兼容还没改的老调用方
        // （以及将来可能有的导入脚本），新建页面本身永远会带上 name。
        String name = StringUtils.hasText(dto.getName())
                ? dto.getName().trim()
                : (className == null ? subjectName : subjectName + " · " + className);

        SubjectContainer c = new SubjectContainer();
        c.setSubjectName(subjectName);
        c.setName(name);
        c.setClassId(dto.getClassId());
        c.setOwnerId(userId);
        c.setStatus(1);
        // 精确到分钟的截止时间优先于"天数"：老师说的"周五晚 8 点前"只有前者表达得了。
        // 天数是老字段，留着兼容，不让两个同时生效（同时传会取更精确的那个）
        if (dto.getInviteExpireAt() != null) {
            if (dto.getInviteExpireAt().isBefore(LocalDateTime.now())) {
                throw new BizException(1001, "截止时间不能早于现在");
            }
            c.setInviteExpireAt(dto.getInviteExpireAt());
        } else if (dto.getInviteExpireDays() != null) {
            c.setInviteExpireAt(LocalDateTime.now().plusDays(dto.getInviteExpireDays()));
        }
        if (StringUtils.hasText(dto.getJoinPassword())) {
            c.setJoinPasswordHash(PasswordHasher.hash(dto.getJoinPassword()));
            c.setJoinPasswordSetAt(LocalDateTime.now());
        }
        insertWithUniqueCode(c);

        // 群主自己也是一行成员记录：成员列表、权限校验都只认这张表，
        // 如果只在 container.owner_id 里记一笔，群主会在自己的成员列表里消失
        ContainerMember owner = new ContainerMember();
        owner.setContainerId(c.getId());
        owner.setUserId(userId);
        owner.setRole(ContainerMember.ROLE_OWNER);
        owner.setStatus(ContainerMember.STATUS_IN);
        owner.setJoinSource(2);
        owner.setJoinedAt(LocalDateTime.now());
        memberMapper.insert(owner);

        return new ContainerCreateVO(c.getId(), c.getInviteCode());
    }

    /**
     * 插入容器并保证邀请码唯一。
     *
     * <p><b>为什么是"撞键重试"而不是"先查再插"</b>：
     * 先查再插在查和插之间有窗口，两个并发请求会同时查到"不存在"然后都插入，
     * 第二个直接抛 500。唯一键是数据库层面的保证，重试是唯一正确的写法。
     */
    private void insertWithUniqueCode(SubjectContainer c) {
        for (int i = 0; i < CODE_RETRY; i++) {
            c.setInviteCode(InviteCodeGenerator.next());
            try {
                containerMapper.insert(c);
                return;
            } catch (DuplicateKeyException e) {
                // 只可能是 uk_invite_code 撞了；其他唯一键冲突不会出现在这张表
                log.warn("邀请码撞车，重试第 {} 次: {}", i + 1, c.getInviteCode());
            }
        }
        throw new BizException(1006, "邀请码生成失败，请重试");
    }

    // ---------------------------------------------------------------
    // 列表 / 详情
    // ---------------------------------------------------------------

    @Override
    public ContainerListVO list(Long userId, String tab, String keyword, Long classId, Integer inviteStatus) {
        classService.requireTeacher(userId);

        // tab 决定查哪个角色。三个角色互斥（一个人在一个容器里只有一个角色），
        // 所以一个容器只会出现在一个 tab 里，不会重复计数
        int wantRole = switch (tab == null ? "created" : tab) {
            case "created" -> ContainerMember.ROLE_OWNER;
            case "managed" -> ContainerMember.ROLE_ADMIN;
            case "joined" -> ContainerMember.ROLE_MEMBER;
            default -> throw new BizException(1001, "tab 只能是 created / managed / joined");
        };

        List<ContainerMember> myRows = memberMapper.selectList(new LambdaQueryWrapper<ContainerMember>()
                .eq(ContainerMember::getUserId, userId)
                .eq(ContainerMember::getStatus, ContainerMember.STATUS_IN)
                .eq(ContainerMember::getRole, wantRole));
        if (myRows.isEmpty()) {
            return new ContainerListVO(0, Collections.emptyList());
        }

        Map<Long, Integer> roleMap = myRows.stream()
                .collect(Collectors.toMap(ContainerMember::getContainerId, ContainerMember::getRole));
        List<Long> ids = new ArrayList<>(roleMap.keySet());

        LambdaQueryWrapper<SubjectContainer> qw = new LambdaQueryWrapper<SubjectContainer>()
                .in(SubjectContainer::getId, ids)
                .eq(SubjectContainer::getStatus, 1);

        if (StringUtils.hasText(keyword)) {
            String k = keyword.trim();
            // OR 必须包在 and(...) 里，否则会把 in(id) 的条件甩开 ——
            // 这就是 engineering-notes §2 里那个最隐蔽的越权写法
            qw.and(w -> w.like(SubjectContainer::getSubjectName, k)
                    .or()
                    .like(SubjectContainer::getName, k));
        }
        if (classId != null) {
            qw.eq(SubjectContainer::getClassId, classId);
        }
        if (inviteStatus != null) {
            LocalDateTime now = LocalDateTime.now();
            switch (inviteStatus) {
                case 1 -> qw.and(w -> w.isNull(SubjectContainer::getInviteExpireAt)
                        .or()
                        .gt(SubjectContainer::getInviteExpireAt, now));
                case 2 -> qw.isNotNull(SubjectContainer::getInviteExpireAt)
                        .le(SubjectContainer::getInviteExpireAt, now);
                case 3 -> qw.isNull(SubjectContainer::getInviteExpireAt);
                default -> throw new BizException(1001, "inviteStatus 只能是 1 / 2 / 3");
            }
        }
        qw.orderByDesc(SubjectContainer::getUpdatedAt);

        List<SubjectContainer> rows = containerMapper.selectList(qw);
        Map<Long, Integer> counts = memberCounts(rows.stream().map(SubjectContainer::getId).toList());
        Map<Long, String> classNames = classNames(rows);
        Map<Long, Integer> studentCounts = studentCounts(rows);

        List<ContainerVO> list = new ArrayList<>(rows.size());
        for (SubjectContainer c : rows) {
            list.add(toVO(c, roleMap.getOrDefault(c.getId(), 0),
                    counts.getOrDefault(c.getId(), 0),
                    c.getClassId() == null ? 0 : studentCounts.getOrDefault(c.getClassId(), 0),
                    /* classNames 的 key 是 classId，不是容器 id。
                       写成 c.getId() 会让每个群显示成隔壁班的名字：
                       容器 2（一班）查到 class 2（二班），二班查到不存在的 class 3 → null。
                       老师照着这个列表找"一班的群"，看到的是"二班"，以为群没了 */
                    c.getClassId() == null ? null : classNames.get(c.getClassId())));
        }
        return new ContainerListVO(list.size(), list);
    }

    /**
     * 每个班有多少在读学生，按 classId 分组。
     *
     * <p>学生不在 t_container_member 里（他们不是 t_user），所以列表的「N 人」
     * 如果只算成员数，一个 57 个学生的群会显示「1 人」。
     * 这里按 classId 批量查一次，不按容器逐个查 —— 列表里可能有几十个群。
     */
    private Map<Long, Integer> studentCounts(List<SubjectContainer> rows) {
        Map<Long, Integer> out = new HashMap<>();
        List<Long> classIds = rows.stream()
                .map(SubjectContainer::getClassId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (classIds.isEmpty()) {
            return out;
        }
        QueryWrapper<Student> qw = new QueryWrapper<>();
        qw.select("class_id AS cid", "COUNT(*) AS cnt")
                .in("class_id", classIds)
                .eq("status", 1)
                .groupBy("class_id");
        for (Map<String, Object> row : studentMapper.selectMaps(qw)) {
            Object cid = row.get("cid");
            Object cnt = row.get("cnt");
            if (cid != null && cnt != null) {
                out.put(((Number) cid).longValue(), ((Number) cnt).intValue());
            }
        }
        return out;
    }

    /**
     * 一次查出所有容器的人数，避免 N+1。
     *
     * <p>用 {@code selectMaps} + 别名而不是给 mapper 加自定义 SQL：
     * 别名故意写成没有下划线的 {@code cid}/{@code cnt}，
     * 这样无论 MyBatis 的驼峰映射怎么配，取出来的 key 都是固定的。
     */
    private Map<Long, Integer> memberCounts(List<Long> containerIds) {
        Map<Long, Integer> out = new HashMap<>();
        if (containerIds.isEmpty()) {
            return out;
        }
        QueryWrapper<ContainerMember> qw = new QueryWrapper<>();
        qw.select("container_id AS cid", "COUNT(*) AS cnt")
                .in("container_id", containerIds)
                .eq("status", ContainerMember.STATUS_IN)
                .groupBy("container_id");
        for (Map<String, Object> row : memberMapper.selectMaps(qw)) {
            Object cid = row.get("cid");
            Object cnt = row.get("cnt");
            if (cid instanceof Number && cnt instanceof Number) {
                out.put(((Number) cid).longValue(), ((Number) cnt).intValue());
            }
        }
        return out;
    }

    /** 批量取容器关联的班级名，同样是为了避免逐个查 */
    private Map<Long, String> classNames(List<SubjectContainer> rows) {
        List<Long> classIds = rows.stream()
                .map(SubjectContainer::getClassId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (classIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return classMapper.selectBatchIds(classIds).stream()
                .collect(Collectors.toMap(ClassInfo::getId, ClassInfo::getName, (a, b) -> a));
    }

    @Override
    public ContainerDetailVO detail(Long userId, Long containerId) {
        ContainerMember me = auth.requireMember(userId, containerId);
        SubjectContainer c = auth.requireContainer(containerId);

        ContainerDetailVO vo = new ContainerDetailVO();
        vo.setId(c.getId());
        vo.setName(c.getName());
        vo.setSubjectName(c.getSubjectName());
        vo.setClassId(c.getClassId());
        vo.setOwnerId(c.getOwnerId());
        vo.setMyRole(me.getRole());
        vo.setHasPassword(c.getJoinPasswordHash() != null);
        vo.setInviteExpireAt(c.getInviteExpireAt());
        vo.setInviteExpired(auth.isInviteExpired(c));
        vo.setMyMuted(me.isMuted());
        vo.setMyMuteUntil(me.getMuteUntil());
        vo.setSessionId(c.getSessionId());
        vo.setCreatedAt(c.getCreatedAt());

        // 邀请码只给群主和班委：普通成员拿到码就能无限拉人进来
        if (me.getRole() != null && me.getRole() <= ContainerMember.ROLE_ADMIN) {
            vo.setInviteCode(c.getInviteCode());
        }

        vo.setMemberCount(memberMapper.selectCount(new LambdaQueryWrapper<ContainerMember>()
                .eq(ContainerMember::getContainerId, c.getId())
                .eq(ContainerMember::getStatus, ContainerMember.STATUS_IN)).intValue());
        /* 本班学生自动在群里，但他们不在成员表里（不是 t_user），
           所以 memberCount 里没有他们。老师看「1 人」会以为群是空的 */
        vo.setStudentCount(c.getClassId() == null ? 0
                : studentMapper.selectCount(new LambdaQueryWrapper<Student>()
                        .eq(Student::getClassId, c.getClassId())
                        .eq(Student::getStatus, 1)).intValue());
        vo.setAdminCount(memberMapper.selectCount(new LambdaQueryWrapper<ContainerMember>()
                .eq(ContainerMember::getContainerId, c.getId())
                .eq(ContainerMember::getStatus, ContainerMember.STATUS_IN)
                .eq(ContainerMember::getRole, ContainerMember.ROLE_ADMIN)).intValue());

        User owner = userMapper.selectById(c.getOwnerId());
        vo.setOwnerName(owner == null ? "未设置姓名" : auth.displayName(null, owner));
        if (c.getClassId() != null) {
            ClassInfo clazz = classMapper.selectById(c.getClassId());
            vo.setClassName(clazz == null ? null : clazz.getName());
        }
        vo.setPermissions(auth.permissionsOf(me.getRole()));
        return vo;
    }

    // ---------------------------------------------------------------
    // 改信息 / 解散
    // ---------------------------------------------------------------

    /**
     * 修改容器信息。<b>全量提交</b>：{@code classId} 传 null 就是解除关联。
     *
     * <p>用 {@code LambdaUpdateWrapper.set()} 显式列字段，不用 {@code updateById} ——
     * 后者会跳过 null，"解除关联"这个操作会静默失败，页面看起来像保存成功了。
     */
    @Override
    public void update(Long userId, Long containerId, ContainerUpdateDTO dto) {
        auth.requireOwner(userId, containerId);
        SubjectContainer c = auth.requireContainer(containerId);

        if (dto.getClassId() != null) {
            ClassInfo clazz = classMapper.selectById(dto.getClassId());
            if (clazz == null) {
                throw new BizException(1004, "关联的班级不存在");
            }
            if (!Objects.equals(clazz.getHeadTeacherId(), userId)) {
                throw new BizException(3002, "只能关联你自己带的班级");
            }
        }

        containerMapper.update(null, new LambdaUpdateWrapper<SubjectContainer>()
                .eq(SubjectContainer::getId, c.getId())
                // 再带一次 owner_id：上面校验过，写进 WHERE 是零成本的兜底
                .eq(SubjectContainer::getOwnerId, userId)
                .set(SubjectContainer::getSubjectName, dto.getSubjectName().trim())
                .set(SubjectContainer::getName, dto.getName().trim())
                .set(SubjectContainer::getClassId, dto.getClassId()));

        opLog.write(c.getId(), userId, null, ContainerOpLog.UPDATE,
                "修改容器信息：" + dto.getSubjectName().trim() + " / " + dto.getName().trim());
    }

    /**
     * 解散容器：软删（status=0），成员关系保留。
     *
     * <p>为什么不是物理删除：成员、精华、日志都指向它，
     * 删了之后"上学期那个数学群怎么没了"就查不出任何痕迹。
     * 解散后所有人访问详情都返回 1004，列表里也不再出现。
     */
    @Override
    public void dissolve(Long userId, Long containerId, DissolveDTO dto) {
        auth.requireOwner(userId, containerId);
        SubjectContainer c = auth.requireContainer(containerId);

        if (!c.getName().equals(dto.getConfirmName().trim())) {
            throw new BizException(1001, "容器名称不一致，未执行解散");
        }

        containerMapper.update(null, new LambdaUpdateWrapper<SubjectContainer>()
                .eq(SubjectContainer::getId, c.getId())
                .eq(SubjectContainer::getOwnerId, userId)
                .eq(SubjectContainer::getStatus, 1)
                .set(SubjectContainer::getStatus, 0)
                .set(SubjectContainer::getDissolvedAt, LocalDateTime.now())
                .set(SubjectContainer::getDissolvedBy, userId));

        opLog.write(c.getId(), userId, null, ContainerOpLog.DISSOLVE, "解散容器：" + c.getName());
    }

    // ---------------------------------------------------------------
    // 邀请码
    // ---------------------------------------------------------------

    @Override
    public InviteVO invite(Long userId, Long containerId) {
        auth.requireAdminOrOwner(userId, containerId);
        SubjectContainer c = auth.requireContainer(containerId);

        InviteVO vo = new InviteVO();
        vo.setContainerId(c.getId());
        vo.setInviteCode(c.getInviteCode());
        vo.setInviteExpireAt(c.getInviteExpireAt());
        vo.setInviteExpired(auth.isInviteExpired(c));
        vo.setHasPassword(c.getJoinPasswordHash() != null);
        vo.setJoinPasswordSetAt(c.getJoinPasswordSetAt());
        return vo;
    }

    /**
     * 改有效期。<b>不换码</b>，所以过期之后把时间改到未来，同一个码就能继续用。
     *
     * <p>三种入参的优先级：days &gt; expireAt &gt; 永久（都不传）。
     * 传 days=0 或负数会被当成非法参数，而不是"永久" ——
     * "0 天"和"永久"是两个相反的意思，不能让一个 0 同时表达。
     */
    @Override
    public void updateExpire(Long userId, Long containerId, InviteExpireDTO dto) {
        auth.requireOwner(userId, containerId);
        SubjectContainer c = auth.requireContainer(containerId);

        LocalDateTime expireAt = null;
        String label;
        if (dto.getDays() != null) {
            if (dto.getDays() < 1 || dto.getDays() > 3650) {
                throw new BizException(1001, "有效期需在 1-3650 天之间");
            }
            expireAt = LocalDateTime.now().plusDays(dto.getDays());
            label = dto.getDays() + " 天后过期";
        } else if (dto.getExpireAt() != null) {
            if (dto.getExpireAt().isBefore(LocalDateTime.now())) {
                throw new BizException(1001, "截止时间不能早于现在");
            }
            expireAt = dto.getExpireAt();
            label = "截止 " + expireAt;
        } else {
            label = "永久有效";
        }

        containerMapper.update(null, new LambdaUpdateWrapper<SubjectContainer>()
                .eq(SubjectContainer::getId, c.getId())
                .eq(SubjectContainer::getOwnerId, userId)
                .set(SubjectContainer::getInviteExpireAt, expireAt));

        opLog.write(c.getId(), userId, null, ContainerOpLog.SET_EXPIRE, "邀请码有效期改为：" + label);
    }

    /**
     * 重新生成邀请码。<b>旧码立即失效</b>（整行换值，旧码再也查不到任何容器）。
     *
     * <p>已经加入的成员不受影响 —— 他们靠成员表访问，不再用邀请码。
     * 这一点必须在前端二次确认里写清楚，否则老师不敢点。
     */
    @Override
    public String regenerateCode(Long userId, Long containerId) {
        auth.requireOwner(userId, containerId);
        SubjectContainer c = auth.requireContainer(containerId);

        for (int i = 0; i < CODE_RETRY; i++) {
            String code = InviteCodeGenerator.next();
            try {
                int n = containerMapper.update(null, new LambdaUpdateWrapper<SubjectContainer>()
                        .eq(SubjectContainer::getId, c.getId())
                        .eq(SubjectContainer::getOwnerId, userId)
                        .set(SubjectContainer::getInviteCode, code));
                if (n == 0) {
                    // 理论上走不到：上面 requireOwner 已经确认过归属
                    throw new BizException(3002, "只有群主可以操作");
                }
                opLog.write(c.getId(), userId, null, ContainerOpLog.REGEN_CODE,
                        "重新生成邀请码，旧码 " + c.getInviteCode() + " 已失效");
                return code;
            } catch (DuplicateKeyException e) {
                log.warn("重新生成邀请码撞车，重试第 {} 次: {}", i + 1, code);
            }
        }
        throw new BizException(1006, "邀请码生成失败，请重试");
    }

    /**
     * 设置 / 修改 / 关闭加入密码。
     *
     * <p>改密码就是换哈希，旧密码<b>立即失效</b>（没有任何地方还留着旧哈希）。
     * 传空 = 关闭密码。日志里只写"设置/关闭"，绝不写密码本身。
     */
    @Override
    public void setJoinPassword(Long userId, Long containerId, JoinPasswordDTO dto) {
        auth.requireOwner(userId, containerId);
        SubjectContainer c = auth.requireContainer(containerId);

        boolean clear = !StringUtils.hasText(dto.getPassword());
        String hash = clear ? null : PasswordHasher.hash(dto.getPassword());
        LocalDateTime setAt = clear ? null : LocalDateTime.now();

        containerMapper.update(null, new LambdaUpdateWrapper<SubjectContainer>()
                .eq(SubjectContainer::getId, c.getId())
                .eq(SubjectContainer::getOwnerId, userId)
                .set(SubjectContainer::getJoinPasswordHash, hash)
                .set(SubjectContainer::getJoinPasswordSetAt, setAt));

        opLog.write(c.getId(), userId, null, ContainerOpLog.SET_PASSWORD, clear ? "关闭加入密码" : "设置加入密码");
    }

    // ---------------------------------------------------------------
    // 操作日志
    // ---------------------------------------------------------------

    /**
     * 操作日志，倒序。不分页总数：日志页只需要"最近发生了什么"，
     * 再往下翻的场景基本不存在，省一次 COUNT。
     */
    @Override
    public List<ContainerLogVO> logs(Long userId, Long containerId, Integer page, Integer size) {
        // requireOwner 内部已经校验过容器存在，不用再 requireContainer 一次
        auth.requireOwner(userId, containerId);

        int p = (page == null || page < 1) ? 1 : page;
        int s = (size == null || size < 1) ? 20 : Math.min(size, 100);
        int offset = (p - 1) * s;

        LambdaQueryWrapper<ContainerOpLog> qw = new LambdaQueryWrapper<ContainerOpLog>()
                .eq(ContainerOpLog::getContainerId, containerId);
        // offset / size 都是钳制过的 int，拼不出非数字内容
        qw.last("ORDER BY created_at DESC, id DESC LIMIT " + offset + "," + s);
        List<ContainerOpLog> rows = opLogMapper.selectList(qw);
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, String> names = userNames(rows.stream()
                .flatMap(r -> Stream.of(r.getOperatorId(), r.getTargetUserId()))
                .filter(Objects::nonNull)
                .distinct()
                .toList());

        List<ContainerLogVO> out = new ArrayList<>(rows.size());
        for (ContainerOpLog r : rows) {
            ContainerLogVO vo = new ContainerLogVO();
            vo.setActionType(r.getActionType());
            vo.setDetail(r.getDetail());
            vo.setOperatorId(r.getOperatorId());
            vo.setOperatorName(names.getOrDefault(r.getOperatorId(), "已注销用户"));
            vo.setTargetUserId(r.getTargetUserId());
            vo.setTargetName(r.getTargetUserId() == null ? null
                    : names.getOrDefault(r.getTargetUserId(), "已注销用户"));
            vo.setCreatedAt(r.getCreatedAt());
            out.add(vo);
        }
        return out;
    }

    /** 批量取用户展示名，key 是 userId */
    private Map<Long, String> userNames(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, String> out = new HashMap<>();
        for (User u : userMapper.selectBatchIds(userIds)) {
            out.put(u.getId(), auth.displayName(null, u));
        }
        return out;
    }

    /** 实体转列表项。inviteCode 按角色决定给不给 */
    private ContainerVO toVO(SubjectContainer c, int myRole, int memberCount, int studentCount, String className) {
        ContainerVO vo = new ContainerVO();
        vo.setId(c.getId());
        vo.setName(c.getName());
        vo.setSubjectName(c.getSubjectName());
        vo.setClassName(className);
        vo.setMyRole(myRole);
        vo.setMemberCount(memberCount);
        vo.setStudentCount(studentCount);
        vo.setInviteExpireAt(c.getInviteExpireAt());
        vo.setInviteExpired(auth.isInviteExpired(c));
        vo.setHasPassword(c.getJoinPasswordHash() != null);
        vo.setCreatedAt(c.getCreatedAt());
        vo.setUpdatedAt(c.getUpdatedAt());
        if (myRole <= ContainerMember.ROLE_ADMIN) {
            vo.setInviteCode(c.getInviteCode());
        }
        return vo;
    }
}
