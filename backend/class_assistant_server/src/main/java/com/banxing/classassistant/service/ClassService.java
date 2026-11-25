package com.banxing.classassistant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.banxing.classassistant.common.BizException;
import com.banxing.classassistant.dto.ClassSaveDTO;
import com.banxing.classassistant.dto.ClassVO;
import com.banxing.classassistant.entity.ClassInfo;
import com.banxing.classassistant.entity.ContainerMember;
import com.banxing.classassistant.entity.Student;
import com.banxing.classassistant.entity.SubjectContainer;
import com.banxing.classassistant.entity.User;
import com.banxing.classassistant.mapper.ClassMapper;
import com.banxing.classassistant.mapper.ContainerMemberMapper;
import com.banxing.classassistant.mapper.StudentMapper;
import com.banxing.classassistant.mapper.SubjectContainerMapper;
import com.banxing.classassistant.mapper.UserMapper;
import com.banxing.classassistant.util.InviteCodeGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 教师端公共上下文：当前登录的是不是老师、他带的是哪几个班。
 *
 * <p><b>v0.5 起从「一个老师一个班」改成「一个老师多个班」。</b>
 * 原来这里用 {@code selectOne(head_teacher_id)} 查班 —— 老师带第二个班的那天，
 * 所有教师端接口会一起抛 {@code TooManyResultsException}，而且报错信息是
 * 「Expected one result but found 2」，跟业务毫无关系，极难定位。
 *
 * <p>现在统一成三条规矩：
 * <ol>
 *   <li>要「一个班」的地方必须传 classId，由 {@link #resolveClass} 校验归属</li>
 *   <li>不传 classId 时退化成「我的第一个班」，只为兼容老调用方，
 *       <b>新接口一律要求显式传</b></li>
 *   <li>越权（传了别人的班）抛 3002，不是「查不到」——
 *       前端要能区分「这个班没了」和「这不是你的班」</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClassService {

    /** 邀请码撞唯一键时的重试次数，和 ContainerServiceImpl 保持一致 */
    private static final int CODE_RETRY = 5;

    private final UserMapper userMapper;
    private final ClassMapper classMapper;
    private final StudentMapper studentMapper;
    private final SubjectContainerMapper containerMapper;
    private final ContainerMemberMapper memberMapper;

    /**
     * 校验当前用户是老师，不是就抛 3001。
     * 返回 User 本身，因为首页要拿它的 name 显示「班主任：张老师」。
     */
    public User requireTeacher(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || user.getRole() == null || user.getRole() != 1) {
            throw new BizException(3001, "当前用户不是老师，无权访问");
        }
        return user;
    }

    /* ==================== 我的班级 ==================== */

    /**
     * 我带的全部班级（未归档），按创建顺序。
     *
     * <p>用 {@code selectList} 而不是 {@code selectOne}：这是多班级改造的核心，
     * 只要还有一个接口用 selectOne，老师带第二个班时它就会炸。
     */
    public List<ClassInfo> myClasses(Long userId) {
        return classMapper.selectList(new LambdaQueryWrapper<ClassInfo>()
                .eq(ClassInfo::getHeadTeacherId, userId)
                .eq(ClassInfo::getStatus, 1)
                .orderByAsc(ClassInfo::getId));
    }

    /**
     * 我的班级 id 列表。做 {@code IN} 查询、归属校验时用。
     */
    public List<Long> myClassIds(Long userId) {
        List<ClassInfo> list = myClasses(userId);
        List<Long> ids = new ArrayList<>(list.size());
        for (ClassInfo c : list) {
            ids.add(c.getId());
        }
        return ids;
    }

    /**
     * 当前老师带的班（第一个）；没绑班返回 null。
     *
     * <p><b>只为兼容保留。</b> 新代码不要用它 —— 它永远指向"第一个班"，
     * 老师在界面上切到第二个班时，用它就会读到错的班。
     */
    public ClassInfo myClass(Long userId) {
        List<ClassInfo> list = myClasses(userId);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 当前老师第一个班的 id；没绑班返回 null。同样只为兼容保留。
     */
    public Long myClassId(Long userId) {
        ClassInfo clazz = myClass(userId);
        return clazz == null ? null : clazz.getId();
    }

    /**
     * 按 classId 解析出班级，并校验这个班确实是当前老师的。
     *
     * @param classId 前端传的班级 id。<b>传 null 表示"用我的第一个班"</b>，
     *                目的是让还没传 classId 的页面在单班老师那里继续能用
     * @return 班级实体；老师一个班都没有时返回 null（不是错误，是"还没建班"）
     * @throws BizException 1004 班级不存在/已归档；3002 这个班不是你的
     */
    public ClassInfo resolveClass(Long userId, Long classId) {
        requireTeacher(userId);

        if (classId == null) {
            return myClass(userId);
        }
        ClassInfo clazz = classMapper.selectById(classId);
        if (clazz == null || clazz.getStatus() == null || clazz.getStatus() != 1) {
            throw new BizException(1004, "班级不存在或已归档");
        }
        // 3002 而不是 1004：改 URL 试别人班级 id 时，必须能看出"越权"而不是"不存在"
        if (!userId.equals(clazz.getHeadTeacherId())) {
            throw new BizException(3002, "这不是你带的班级");
        }
        return clazz;
    }

    /**
     * 同 {@link #resolveClass}，只要 id。
     */
    public Long resolveClassId(Long userId, Long classId) {
        ClassInfo clazz = resolveClass(userId, classId);
        return clazz == null ? null : clazz.getId();
    }

    /**
     * 同 {@link #resolveClass}，但"没班"是硬错误。
     *
     * <p>写操作（新增学生、建考试、保存座位）用它：没班就没地方落库，
     * 与其静默失败，不如明确告诉老师「先建班」。
     */
    public Long requireClassId(Long userId, Long classId) {
        Long id = resolveClassId(userId, classId);
        if (id == null) {
            throw new BizException(1001, "你还没有班级，请先新建一个班级");
        }
        return id;
    }

    /**
     * 这个学生是不是我带的班里的。
     *
     * <p>学生详情 / 编辑 / 移出 这三个接口都按 studentId 定位，
     * 不能再假设"我只有一个班"。这里直接看学生所属班级的班主任是不是我。
     */
    public boolean ownsStudent(Long userId, Student s) {
        if (s == null || s.getClassId() == null) {
            return false;
        }
        ClassInfo clazz = classMapper.selectById(s.getClassId());
        /* 必须一起判 status：班级归档后，学生行还留在表里，
           只看 head_teacher_id 的话，拿旧 studentId 直接调 /student/{id}
           还能读到、甚至改到一个"已经删掉的班"的学生 */
        return clazz != null
                && clazz.getStatus() != null && clazz.getStatus() == 1
                && userId.equals(clazz.getHeadTeacherId());
    }

    /* ==================== 通讯录 / 选择器用的班级列表 ==================== */

    /**
     * 我带的班级 + 每个班的在读人数 + 对应的班级群。
     *
     * <p>一次返回三样东西，是因为首页选择器和通讯录都要这三样：
     * 选择器要「班级名 + 人数」，通讯录要「班级群入口 + 联系人分组」。
     * 拆成三个接口的话，两个页面各要发三次请求，而且中间任何一个失败都会半截。
     *
     * <p>人数和群成员数用 {@code selectMaps + GROUP BY} 一次查完，
     * 不在循环里逐班 count（班级数一多就是 N+1）。
     */
    public List<ClassVO> list(Long userId) {
        requireTeacher(userId);

        List<ClassInfo> classes = myClasses(userId);
        if (classes.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> classIds = new ArrayList<>(classes.size());
        for (ClassInfo c : classes) {
            classIds.add(c.getId());
        }

        Map<Long, Integer> stuCount = countByKey(
                studentMapper.selectMaps(new QueryWrapper<Student>()
                        .select("class_id AS cid", "COUNT(*) AS cnt")
                        .in("class_id", classIds)
                        .eq("status", 1)
                        .groupBy("class_id")));

        // 每个班最多一个班级群。历史数据里可能有多个（老师手动建过同班群），
        // 取 id 最小的那个当"班级群"，其余仍留在课程群组列表里
        List<SubjectContainer> groups = containerMapper.selectList(
                new LambdaQueryWrapper<SubjectContainer>()
                        .in(SubjectContainer::getClassId, classIds)
                        .eq(SubjectContainer::getStatus, 1)
                        .orderByAsc(SubjectContainer::getId));
        Map<Long, SubjectContainer> groupByClass = new HashMap<>();
        List<Long> groupIds = new ArrayList<>();
        for (SubjectContainer g : groups) {
            if (!groupByClass.containsKey(g.getClassId())) {
                groupByClass.put(g.getClassId(), g);
                groupIds.add(g.getId());
            }
        }

        Map<Long, Integer> memberCount = groupIds.isEmpty()
                ? Collections.emptyMap()
                : countByKey(memberMapper.selectMaps(new QueryWrapper<ContainerMember>()
                        .select("container_id AS cid", "COUNT(*) AS cnt")
                        .in("container_id", groupIds)
                        .eq("status", ContainerMember.STATUS_IN)
                        .groupBy("container_id")));

        List<ClassVO> out = new ArrayList<>(classes.size());
        for (ClassInfo c : classes) {
            ClassVO vo = new ClassVO();
            vo.setId(c.getId());
            vo.setName(c.getName());
            vo.setGrade(c.getGrade());
            vo.setTermStartDate(c.getTermStartDate());
            vo.setInviteCode(c.getInviteCode());
            vo.setStudentCount(stuCount.getOrDefault(c.getId(), 0));
            SubjectContainer g = groupByClass.get(c.getId());
            if (g != null) {
                vo.setContainerId(g.getId());
                vo.setContainerName(g.getName());
                vo.setGroupMemberCount(memberCount.getOrDefault(g.getId(), 0));
                /* 学生自动在群里（按 classId 派生），但不在 t_container_member 里。
                   总数必须把两边加起来，否则学生端显示 59 人、老师端显示 1 人 */
                vo.setGroupTotalCount(memberCount.getOrDefault(g.getId(), 0)
                        + stuCount.getOrDefault(c.getId(), 0));
            } else {
                vo.setGroupMemberCount(0);
                vo.setGroupTotalCount(0);
            }
            out.add(vo);
        }
        return out;
    }

    /**
     * 班级设置页要的数据。
     */
    public ClassVO detail(Long userId, Long classId) {
        requireTeacher(userId);
        ClassInfo clazz = resolveClass(userId, classId);
        if (clazz == null) {
            throw new BizException(1001, "你还没有班级");
        }

        ClassVO vo = new ClassVO();
        vo.setId(clazz.getId());
        vo.setName(clazz.getName());
        vo.setGrade(clazz.getGrade());
        vo.setTermStartDate(clazz.getTermStartDate());
        vo.setInviteCode(clazz.getInviteCode());
        vo.setStudentCount(studentMapper.selectCount(new LambdaQueryWrapper<Student>()
                .eq(Student::getClassId, clazz.getId())
                .eq(Student::getStatus, 1)).intValue());

        SubjectContainer g = findClassGroup(clazz.getId());
        if (g != null) {
            vo.setContainerId(g.getId());
            vo.setContainerName(g.getName());
            int teachers = memberMapper.selectCount(new LambdaQueryWrapper<ContainerMember>()
                    .eq(ContainerMember::getContainerId, g.getId())
                    .eq(ContainerMember::getStatus, ContainerMember.STATUS_IN)).intValue();
            vo.setGroupMemberCount(teachers);
            vo.setGroupTotalCount(teachers + (vo.getStudentCount() == null ? 0 : vo.getStudentCount()));
        }
        return vo;
    }

    /**
     * 修改班级名 / 年级 / 开学日期。
     *
     * <p>「传什么改什么」而不是「全量覆盖」：传 null 的字段不进 SET 子句。
     * 名单导入页要一键把班名改成名单里那个，它只想改名字，
     * 如果接口要求必须连年级和开学日期一起传，前端就得先 GET 一次再 PUT ——
     * 多一次往返，而且 GET 回来的值一旦是 null，PUT 上去就把数据清空了。
     *
     * <p>不加 {@code @Transactional}：单表单行 UPDATE，本身就是一个原子操作。
     */
    public void update(Long userId, Long classId, ClassSaveDTO dto) {
        requireTeacher(userId);
        ClassInfo clazz = resolveClass(userId, classId);
        if (clazz == null) {
            throw new BizException(1001, "你还没有班级");
        }

        String name = dto.getName().trim();
        boolean renamed = !name.equals(clazz.getName());

        /* 改名要查重，而且要排除自己。
           只做 create 时的查重是不够的：把「一班」改成「二班」，
           两个班同名之后，首页选择器和通讯录里会出现两张一模一样的卡，
           老师根本分不清点的是哪个 —— 这个坑比建班时重名更难发现，
           因为建班时一眼就报错了，改名却会静默成功 */
        if (renamed) {
            Long dup = classMapper.selectCount(new LambdaQueryWrapper<ClassInfo>()
                    .eq(ClassInfo::getHeadTeacherId, userId)
                    .eq(ClassInfo::getName, name)
                    .eq(ClassInfo::getStatus, 1)
                    .ne(ClassInfo::getId, clazz.getId()));
            if (dup != null && dup > 0) {
                throw new BizException(1006, "你已经有一个叫「" + name + "」的班级了");
            }
        }

        LambdaUpdateWrapper<ClassInfo> w = new LambdaUpdateWrapper<ClassInfo>()
                .eq(ClassInfo::getId, clazz.getId())
                // 再带一次 head_teacher_id：上面虽然校验过，但写进 WHERE 是零成本的兜底
                .eq(ClassInfo::getHeadTeacherId, userId)
                .set(ClassInfo::getName, name);

        // 空串是「把年级清空」，是有意义的操作，所以只判 null 不判空
        if (dto.getGrade() != null) {
            w.set(ClassInfo::getGrade, dto.getGrade().trim());
        }
        // term_start_date 是 NOT NULL，没有「清空」这个语义，null 一律当作没传
        if (dto.getTermStartDate() != null) {
            w.set(ClassInfo::getTermStartDate, dto.getTermStartDate());
        }

        classMapper.update(null, w);

        /* 班名改了，班级群名跟着改。
           原来这里的注释是「群也可以有自己的名字，所以不同步」，
           但通讯录里班级群就是班级的入口，卡片上同时出现「24级软通1班」和
           「24级软通2班」两个名字，老师只会以为点错了班。
           班级群是系统自动建的、一个班只有一个，它不该有独立的名字。 */
        if (renamed) {
            SubjectContainer g = findClassGroup(clazz.getId());
            if (g != null) {
                containerMapper.update(null, new LambdaUpdateWrapper<SubjectContainer>()
                        .eq(SubjectContainer::getId, g.getId())
                        .set(SubjectContainer::getName, name)
                        // subject_name 是群聊列表里显示的那个，也要一起改
                        .set(SubjectContainer::getSubjectName, name));
            }
        }
    }

    /**
     * 删除班级。
     *
     * <p><b>删除 = 归档（status=0），不是物理删除。</b>
     * 一个班名下有学生、成绩、座位、请假、待办、聊天记录，散在七八张表里。
     * 物理删除要么写一长串级联，要么留下永远查不到的孤儿行 ——
     * 前者只要漏一张表就是脏数据，后者会让「新建同名班级」读到旧数据。
     * 归档只动两个字段，所有查询本来就带 {@code status=1}，天然隔离。
     *
     * <p>连带的班级群也要一起归档：否则它会留在「课程群组」列表里，
     * 点进去是一个已经没有班级的群，还能继续发消息。
     *
     * <p>允许删到最后一个班。老师带完一届要清空是正常需求，
     * 首页和通讯录都有「还没有班级」的空状态，不会崩。
     */
    @Transactional
    public void delete(Long userId, Long classId) {
        ClassInfo clazz = resolveClass(userId, classId);
        if (clazz == null) {
            throw new BizException(1001, "你还没有班级");
        }

        classMapper.update(null, new LambdaUpdateWrapper<ClassInfo>()
                .eq(ClassInfo::getId, clazz.getId())
                .eq(ClassInfo::getHeadTeacherId, userId)
                .set(ClassInfo::getStatus, 0));

        containerMapper.update(null, new LambdaUpdateWrapper<SubjectContainer>()
                .eq(SubjectContainer::getClassId, clazz.getId())
                .eq(SubjectContainer::getStatus, 1)
                .set(SubjectContainer::getStatus, 0));

        log.info("老师 {} 归档了班级 {}（{}）", userId, clazz.getId(), clazz.getName());
    }

    /**
     * 新建班级，并<b>同时建好这个班的班级群</b>（班主任 = 群主）。
     *
     * <p>为什么建班必须连带建群：需求里"班级群"不是一个可选项，
     * 它是通讯录里这个班的入口。建了班却没有群，老师进通讯录会看到一个空班，
     * 还得再手动去课程群组里建一个再回来关联 —— 三步操作，两步是多余的。
     *
     * <p>三张表（t_class / t_subject_container / t_container_member）都要写，
     * 所以必须 {@code @Transactional}：中途失败会留下"有班没群"或"有群没群主"的脏数据。
     */
    @Transactional
    public Long create(Long userId, ClassSaveDTO dto) {
        requireTeacher(userId);

        String name = dto.getName().trim();
        // 同一个老师不能有两个同名班：首页选择器里两行一模一样的字，根本分不清点的是哪个
        Long dup = classMapper.selectCount(new LambdaQueryWrapper<ClassInfo>()
                .eq(ClassInfo::getHeadTeacherId, userId)
                .eq(ClassInfo::getName, name)
                .eq(ClassInfo::getStatus, 1));
        if (dup != null && dup > 0) {
            throw new BizException(1006, "你已经有一个叫「" + name + "」的班级了");
        }

        ClassInfo c = new ClassInfo();
        c.setName(name);
        // grade 列是 NOT NULL DEFAULT ''，null 会插失败
        c.setGrade(dto.getGrade() == null ? "" : dto.getGrade().trim());
        c.setHeadTeacherId(userId);
        c.setTermStartDate(dto.getTermStartDate() == null ? LocalDate.now() : dto.getTermStartDate());
        c.setStatus(1);
        insertClassWithUniqueCode(c);

        ensureClassGroup(c, userId);
        return c.getId();
    }

    /**
     * 确保这个班有一个班级群，没有就建一个。返回群 id。
     *
     * <p>幂等：已经有群就直接返回。建班和"给历史数据补群"都走这一条路径，
     * 补数据时不用另写一套逻辑。
     */
    @Transactional
    public Long ensureClassGroup(ClassInfo clazz, Long ownerId) {
        SubjectContainer exist = findClassGroup(clazz.getId());
        if (exist != null) {
            return exist.getId();
        }

        SubjectContainer g = new SubjectContainer();
        // 群名跟班级名走：老师改名后群名不会自动同步（群也可以有自己的名字），
        // 但新建的那一刻必须一致，否则通讯录里"班级群"和"班级"两个名字对不上
        g.setSubjectName(clazz.getName());
        g.setName(clazz.getName());
        g.setClassId(clazz.getId());
        g.setOwnerId(ownerId);
        g.setStatus(1);
        insertContainerWithUniqueCode(g);

        // 群主自己也要有一行成员记录：成员列表和权限校验只认 t_container_member
        ContainerMember owner = new ContainerMember();
        owner.setContainerId(g.getId());
        owner.setUserId(ownerId);
        owner.setRole(ContainerMember.ROLE_OWNER);
        owner.setStatus(ContainerMember.STATUS_IN);
        owner.setJoinSource(2); // 2 = 建群时自动加入
        memberMapper.insert(owner);
        return g.getId();
    }

    /**
     * 给指定班级补建班级群（幂等）。给"历史班级"和"建群时失败"兜底。
     */
    @Transactional
    public Long ensureClassGroup(Long userId, Long classId) {
        ClassInfo clazz = resolveClass(userId, classId);
        if (clazz == null) {
            throw new BizException(1001, "你还没有班级");
        }
        return ensureClassGroup(clazz, userId);
    }

    /** 这个班的班级群（可能没有）。取 id 最小的那个，和历史数据兼容 */
    public SubjectContainer findClassGroup(Long classId) {
        List<SubjectContainer> rows = containerMapper.selectList(
                new LambdaQueryWrapper<SubjectContainer>()
                        .eq(SubjectContainer::getClassId, classId)
                        .eq(SubjectContainer::getStatus, 1)
                        .orderByAsc(SubjectContainer::getId)
                        .last("LIMIT 1"));
        return rows.isEmpty() ? null : rows.get(0);
    }

    /* ==================== 私有工具 ==================== */

    /**
     * selectMaps 的 (cid, cnt) 两列转 Map。
     * 列名用 cid / cnt 而不是 class_id / count：别名带下划线时，
     * 不同驱动返回的 key 大小写不一致，取不到值。
     */
    private Map<Long, Integer> countByKey(List<Map<String, Object>> rows) {
        Map<Long, Integer> map = new HashMap<>();
        if (rows == null) {
            return map;
        }
        for (Map<String, Object> m : rows) {
            Object k = m.get("cid");
            Object v = m.get("cnt");
            if (k instanceof Number && v instanceof Number) {
                map.put(((Number) k).longValue(), ((Number) v).intValue());
            }
        }
        return map;
    }

    /** 插班级，邀请码撞唯一键就换一个重试 */
    private void insertClassWithUniqueCode(ClassInfo c) {
        for (int i = 0; i < CODE_RETRY; i++) {
            c.setInviteCode(InviteCodeGenerator.next());
            try {
                classMapper.insert(c);
                return;
            } catch (DuplicateKeyException e) {
                log.warn("班级邀请码撞车，重试第 {} 次: {}", i + 1, c.getInviteCode());
            }
        }
        throw new BizException(1006, "邀请码生成失败，请重试");
    }

    /** 插群，邀请码撞唯一键就换一个重试 */
    private void insertContainerWithUniqueCode(SubjectContainer g) {
        for (int i = 0; i < CODE_RETRY; i++) {
            g.setInviteCode(InviteCodeGenerator.next());
            try {
                containerMapper.insert(g);
                return;
            } catch (DuplicateKeyException e) {
                log.warn("群邀请码撞车，重试第 {} 次: {}", i + 1, g.getInviteCode());
            }
        }
        throw new BizException(1006, "邀请码生成失败，请重试");
    }
}
