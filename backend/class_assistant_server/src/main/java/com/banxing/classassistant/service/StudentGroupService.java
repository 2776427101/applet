package com.banxing.classassistant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.banxing.classassistant.common.BizException;
import com.banxing.classassistant.dto.StudentGroupJoinVO;
import com.banxing.classassistant.dto.StudentGroupSendDTO;
import com.banxing.classassistant.dto.StudentGroupVO;
import com.banxing.classassistant.dto.StudentMessageVO;
import com.banxing.classassistant.dto.UploadVO;
import com.banxing.classassistant.entity.ClassInfo;
import com.banxing.classassistant.entity.ContainerMember;
import com.banxing.classassistant.entity.ContainerMessage;
import com.banxing.classassistant.entity.Student;
import com.banxing.classassistant.entity.StudentContainer;
import com.banxing.classassistant.entity.SubjectContainer;
import com.banxing.classassistant.entity.User;
import com.banxing.classassistant.mapper.ClassMapper;
import com.banxing.classassistant.mapper.ContainerMemberMapper;
import com.banxing.classassistant.mapper.ContainerMessageMapper;
import com.banxing.classassistant.mapper.StudentContainerMapper;
import com.banxing.classassistant.mapper.StudentMapper;
import com.banxing.classassistant.mapper.SubjectContainerMapper;
import com.banxing.classassistant.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 学生端的群聊 —— <b>就是老师端那些群，不是另建一套</b>
 *
 * <p>v0.39 这里曾经是一个独立的「班级群」（表 {@code t_class_message}），
 * 结果老师端根本看不到：老师在「24计算机软通1班」群里发了 22 条消息，
 * 学生打开"班级群"是一间空屋子；老师把群邀请码发给学生，学生输入后提示"无效"。
 * 因为它们本来就是两份数据，只是名字撞了。
 *
 * <p>现在统一到 {@code t_subject_container}：老师建的那个群，就是学生看到的群，
 * 一份消息、一个邀请码。
 *
 * <p>可见性两条，覆盖了"进群"的全部情况：
 * <ol>
 *   <li><b>本班的群自动可见</b> —— {@code container.class_id} 等于我的 classId。
 *       花名册里有你，你就在群里，不需要邀请码。这是"一个班一个群"的落点。</li>
 *   <li><b>用邀请码加入别的群</b> —— 记在 {@code t_student_container}。
 *       跨班旁听、{@code class_id} 为空的公共群（如高数群）都走这条。</li>
 * </ol>
 *
 * <p><b>为什么学生不写进 t_container_member：</b> 那张表的 user_id 指向 t_user，
 * 学生不是 t_user。硬塞进去，老师端的成员管理（禁言、设班委、移除）会把学生
 * 当成老师处理；而且两套自增 id 都有 121 号，会静默串成同一个人。
 * 学生用"班级"这一层关系天然在群里，不需要成员行。
 */
@Service
@RequiredArgsConstructor
public class StudentGroupService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    /** 一次拉多少条。3 秒轮询一次，拉太多没有意义，反而拖慢首屏 */
    private static final int DEFAULT_SIZE = 30;

    private static final int MAX_SIZE = 50;

    private final ContainerMessageMapper messageMapper;
    private final ContainerMemberMapper memberMapper;
    private final SubjectContainerMapper containerMapper;
    private final StudentContainerMapper joinedMapper;
    private final StudentMapper studentMapper;
    private final UserMapper userMapper;
    private final ClassMapper classMapper;
    private final ContainerFileService fileService;
    private final StudentDataService studentData;

    // ---------------------------------------------------------------
    // 群列表
    // ---------------------------------------------------------------

    /**
     * 我能看到的群：本班的（自动）+ 用邀请码加入的（记录在案）。
     *
     * <p>顺序：本班的排在前面 —— 那是每天要看的群；跨班加入的是"偶尔瞄一眼"。
     */
    public List<StudentGroupVO> list(Long classId, Long studentId) {
        studentData.requireStudent(classId, studentId);

        List<SubjectContainer> rows = new ArrayList<>(containerMapper.selectList(
                new LambdaQueryWrapper<SubjectContainer>()
                        .eq(SubjectContainer::getClassId, classId)
                        .eq(SubjectContainer::getStatus, 1)
                        .orderByAsc(SubjectContainer::getId)));

        Set<Long> seen = new HashSet<>();
        for (SubjectContainer c : rows) {
            seen.add(c.getId());
        }

        List<Long> extraIds = new ArrayList<>();
        for (StudentContainer j : joinedMapper.selectList(
                new LambdaQueryWrapper<StudentContainer>()
                        .eq(StudentContainer::getStudentId, studentId))) {
            if (!seen.contains(j.getContainerId())) {
                extraIds.add(j.getContainerId());
            }
        }
        if (!extraIds.isEmpty()) {
            rows.addAll(containerMapper.selectList(new LambdaQueryWrapper<SubjectContainer>()
                    .in(SubjectContainer::getId, extraIds)
                    .eq(SubjectContainer::getStatus, 1)
                    .orderByAsc(SubjectContainer::getId)));
        }

        List<StudentGroupVO> out = new ArrayList<>(rows.size());
        for (SubjectContainer c : rows) {
            out.add(toVO(c, classId));
        }
        return out;
    }

    /** 单个群的信息（聊天页顶部：群名 + 人数） */
    public StudentGroupVO info(Long classId, Long studentId, Long containerId) {
        studentData.requireStudent(classId, studentId);
        return toVO(requireVisible(containerId, classId, studentId), classId);
    }

    // ---------------------------------------------------------------
    // 消息
    // ---------------------------------------------------------------

    /**
     * 拉消息。
     *
     * <p>{@code afterId > 0} 是轮询用的增量：只要比它新的，按 id 升序 ——
     * 增量必须升序，否则新消息会被插到列表最上面。
     * 不带 afterId 是首次进入：取最近 N 条，再翻成升序。
     */
    public List<StudentMessageVO> messages(Long classId, Long studentId, Long containerId,
                                           Long afterId, Integer size) {
        studentData.requireStudent(classId, studentId);
        requireVisible(containerId, classId, studentId);

        int cap = (size == null || size <= 0) ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);

        LambdaQueryWrapper<ContainerMessage> qw = new LambdaQueryWrapper<ContainerMessage>()
                .eq(ContainerMessage::getContainerId, containerId)
                .eq(ContainerMessage::getStatus, ContainerMessage.STATUS_NORMAL);

        boolean incremental = afterId != null && afterId > 0;
        if (incremental) {
            qw.gt(ContainerMessage::getId, afterId).orderByAsc(ContainerMessage::getId);
        } else {
            qw.orderByDesc(ContainerMessage::getId);
        }
        qw.last("LIMIT " + cap);

        List<ContainerMessage> rows = messageMapper.selectList(qw);
        if (!incremental) {
            Collections.reverse(rows);
        }

        /* 老师消息的姓名不在消息表里（只有学生存了快照），要回 t_user 查。
           一次批量查，不在循环里查 —— 一屏 30 条可能有 5 个不同的老师 */
        Map<Long, String> teacherNames = teacherNames(rows);

        List<StudentMessageVO> out = new ArrayList<>(rows.size());
        for (ContainerMessage m : rows) {
            out.add(toVO(m, studentId, teacherNames));
        }
        return out;
    }

    /** 老师消息的展示名：t_user.name，查不到就叫"老师" */
    private Map<Long, String> teacherNames(List<ContainerMessage> rows) {
        List<Long> ids = new ArrayList<>();
        for (ContainerMessage m : rows) {
            boolean fromStudent = m.getSenderType() != null
                    && m.getSenderType() == ContainerMessage.FROM_STUDENT;
            if (!fromStudent && m.getSenderId() != null) {
                ids.add(m.getSenderId());
            }
        }
        Map<Long, String> out = new HashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        for (User u : userMapper.selectBatchIds(ids)) {
            if (StringUtils.hasText(u.getName())) {
                out.put(u.getId(), u.getName());
            }
        }
        return out;
    }

    @Transactional(rollbackFor = Exception.class)
    public StudentMessageVO send(Long classId, Long studentId, Long containerId,
                                 StudentGroupSendDTO dto) {
        Student me = studentData.requireStudent(classId, studentId);
        requireVisible(containerId, classId, studentId);

        /* 和老师端 ContainerMessageServiceImpl.send 同一套分支。
           两端发的是同一张表：老师能发图、学生不能，群里的气泡就会长成两种样子 */
        int type = dto == null || dto.getType() == null
                ? ContainerMessage.TYPE_TEXT : dto.getType();
        String content = dto == null || dto.getContent() == null ? "" : dto.getContent().trim();
        String mediaUrl = null;
        String fileName = null;
        Long fileSize = null;
        Integer duration = null;

        switch (type) {
            case ContainerMessage.TYPE_TEXT -> {
                if (content.isEmpty()) {
                    throw new BizException(1001, "说点什么吧");
                }
                if (content.length() > 500) {
                    throw new BizException(1001, "一条最多 500 字");
                }
            }
            case ContainerMessage.TYPE_IMAGE, ContainerMessage.TYPE_VIDEO -> {
                mediaUrl = requireUploadedUrl(dto.getMediaUrl());
                fileName = clipName(dto.getFileName());
                fileSize = dto.getFileSize();
                if (type == ContainerMessage.TYPE_VIDEO) {
                    duration = dto.getDuration();
                }
                content = "";
            }
            case ContainerMessage.TYPE_FILE -> {
                mediaUrl = requireUploadedUrl(dto.getMediaUrl());
                fileName = clipName(dto.getFileName());
                if (!StringUtils.hasText(fileName)) {
                    throw new BizException(1001, "缺少文件名");
                }
                fileSize = dto.getFileSize();
                content = "";
            }
            default -> throw new BizException(1001, "不支持的消息类型");
        }

        ContainerMessage m = new ContainerMessage();
        m.setContainerId(containerId);
        m.setSenderId(studentId);
        /* 这两个字段是学生消息的全部身份：id 会跟老师的撞号，名字必须存快照 */
        m.setSenderType(ContainerMessage.FROM_STUDENT);
        m.setSenderName(StringUtils.hasText(me.getName()) ? me.getName() : "同学");
        m.setContent(content);
        m.setType(type);
        m.setMediaUrl(mediaUrl);
        m.setFileName(fileName);
        m.setFileSize(fileSize);
        m.setDuration(duration);
        m.setStatus(ContainerMessage.STATUS_NORMAL);
        m.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(m);

        /* 直接返回组装好的那条，前端 push 进列表就行。
           不返回的话前端要么再拉一次（多一次往返），要么自己拼一个 id=0 的假消息
           （下一轮轮询会把真的那条再推一遍，出现两条一样的） */
        return toVO(m, studentId);
    }

    /**
     * 学生传附件（图片 / 视频 / 文件）。
     *
     * <p>先判"这个群我看不看得见"，再交给 {@link ContainerFileService} 落盘。
     * 顺序不能反：反了的话，任何一个登录学生都能往别人的群目录里写文件。
     */
    public UploadVO upload(Long classId, Long studentId, Long containerId,
                           MultipartFile file, String kind) {
        studentData.requireStudent(classId, studentId);
        requireVisible(containerId, classId, studentId);
        return fileService.uploadForStudent(containerId, file, kind);
    }

    /**
     * 退出一个"用邀请码加进来的群"。
     *
     * <p>本班的群退不掉：学生在不在群里由 {@code class_id} 派生，删一行
     * {@code t_student_container} 也改变不了什么 —— 下次打开还在。所以直接拒绝，
     * 而不是给一个点了没反应的按钮。
     */
    @Transactional(rollbackFor = Exception.class)
    public void leave(Long classId, Long studentId, Long containerId) {
        studentData.requireStudent(classId, studentId);
        SubjectContainer c = requireVisible(containerId, classId, studentId);

        if (c.getClassId() != null && c.getClassId().equals(classId)) {
            throw new BizException(1001, "本班的群退不了，这是班级的一部分");
        }

        joinedMapper.delete(new LambdaQueryWrapper<StudentContainer>()
                .eq(StudentContainer::getStudentId, studentId)
                .eq(StudentContainer::getContainerId, containerId));
    }

    // ---------------------------------------------------------------
    // 用邀请码加入别的群
    // ---------------------------------------------------------------

    /**
     * 凭邀请码加入一个群。
     *
     * <p>本班的群本来就看得见，走这里会返回 {@code alreadyIn=true} —— 不报错，
     * 因为学生多半只是"再确认一次"。真正需要它的是跨班群和公共群。
     */
    @Transactional(rollbackFor = Exception.class)
    public StudentGroupJoinVO joinByCode(Long classId, Long studentId, String rawCode) {
        studentData.requireStudent(classId, studentId);

        String code = rawCode == null ? "" : rawCode.trim();
        if (!StringUtils.hasText(code)) {
            throw new BizException(1001, "请填邀请码");
        }

        SubjectContainer c = findByInviteCode(code);
        if (c == null || c.getStatus() == null || c.getStatus() != 1) {
            throw new BizException(1004, "邀请码不对，或者这个群已经解散了");
        }
        if (c.getInviteExpireAt() != null && c.getInviteExpireAt().isBefore(LocalDateTime.now())) {
            throw new BizException(1008, "邀请码过期了，找老师重新要一个");
        }
        /* 设了加入密码的群：学生端没有输入密码的入口，与其让他反复试，
           不如直接说清楚找谁。真要用，下一版把密码一起加上 */
        if (StringUtils.hasText(c.getJoinPasswordHash())) {
            throw new BizException(1001, "这个群设了加入密码，学生端还进不去，让班主任去掉密码");
        }

        StudentGroupJoinVO vo = new StudentGroupJoinVO();

        boolean classGroup = c.getClassId() != null && c.getClassId().equals(classId);
        if (classGroup) {
            vo.setGroup(toVO(c, classId));
            vo.setAlreadyIn(true);
            return vo;
        }

        Long exists = joinedMapper.selectCount(new LambdaQueryWrapper<StudentContainer>()
                .eq(StudentContainer::getStudentId, studentId)
                .eq(StudentContainer::getContainerId, c.getId()));
        if (exists != null && exists > 0) {
            vo.setGroup(toVO(c, classId));
            vo.setAlreadyIn(true);
            return vo;
        }

        StudentContainer j = new StudentContainer();
        j.setStudentId(studentId);
        j.setContainerId(c.getId());
        j.setClassId(classId);
        j.setCreatedAt(LocalDateTime.now());
        joinedMapper.insert(j);

        vo.setGroup(toVO(c, classId));
        vo.setAlreadyIn(false);
        return vo;
    }

    // ---------------------------------------------------------------
    // 内部
    // ---------------------------------------------------------------

    /**
     * 取一个我能看的群，看不到就抛。
     *
     * <p>两条判断和 {@link #list} 完全对应 —— 列表里能看到、点进去说没权限，
     * 是最容易让人以为"小程序坏了"的一种不一致。
     */
    private SubjectContainer requireVisible(Long containerId, Long classId, Long studentId) {
        SubjectContainer c = containerId == null ? null : containerMapper.selectById(containerId);
        if (c == null || c.getStatus() == null || c.getStatus() != 1) {
            throw new BizException(1004, "这个群不存在，或者已经解散了");
        }
        if (c.getClassId() != null && c.getClassId().equals(classId)) {
            return c;
        }
        Long hit = joinedMapper.selectCount(new LambdaQueryWrapper<StudentContainer>()
                .eq(StudentContainer::getStudentId, studentId)
                .eq(StudentContainer::getContainerId, containerId));
        if (hit == null || hit == 0) {
            throw new BizException(3002, "你不在这个群里，找班主任要邀请码");
        }
        return c;
    }

    /**
     * 按邀请码找群。
     *
     * <p>大小写不敏感：邀请码是大写字母 + 数字，学生抄的时候经常全小写。
     * 只做"原样"和"转大写"两次，不做模糊匹配 —— 模糊匹配会让差一个字母的码
     * 也命中别的群。
     */
    private SubjectContainer findByInviteCode(String code) {
        SubjectContainer hit = containerMapper.selectOne(new LambdaQueryWrapper<SubjectContainer>()
                .eq(SubjectContainer::getInviteCode, code)
                .last("LIMIT 1"));
        if (hit != null) {
            return hit;
        }
        return containerMapper.selectOne(new LambdaQueryWrapper<SubjectContainer>()
                .eq(SubjectContainer::getInviteCode, code.toUpperCase())
                .last("LIMIT 1"));
    }

    private StudentGroupVO toVO(SubjectContainer c, Long myClassId) {
        StudentGroupVO vo = new StudentGroupVO();
        vo.setId(c.getId());
        vo.setName(StringUtils.hasText(c.getName()) ? c.getName() : c.getSubjectName());
        vo.setSubjectName(c.getSubjectName());

        boolean classGroup = c.getClassId() != null && c.getClassId().equals(myClassId);
        vo.setClassGroup(classGroup);
        /* 本班的群退不了 —— 学生的"在群里"由 class_id 派生，删记录也退不掉。
           前端据此决定群信息页有没有「退出群聊」 */
        vo.setCanLeave(!classGroup);

        int teachers = countTeachers(c);
        int students = countStudents(c);
        vo.setTeacherCount(teachers);
        vo.setStudentCount(students);
        vo.setMemberCount(teachers + students);

        if (c.getClassId() != null) {
            ClassInfo clazz = classMapper.selectById(c.getClassId());
            vo.setClassName(clazz == null ? null : clazz.getName());
        }
        if (c.getOwnerId() != null) {
            User owner = userMapper.selectById(c.getOwnerId());
            if (owner != null) {
                vo.setOwnerName(StringUtils.hasText(owner.getName()) ? owner.getName() : "老师");
            }
        }

        ContainerMessage last = messageMapper.selectOne(new LambdaQueryWrapper<ContainerMessage>()
                .eq(ContainerMessage::getContainerId, c.getId())
                .eq(ContainerMessage::getStatus, ContainerMessage.STATUS_NORMAL)
                .orderByDesc(ContainerMessage::getId)
                .last("LIMIT 1"));
        if (last != null) {
            vo.setLastContent(previewOf(last));
            vo.setLastTime(last.getCreatedAt() == null ? "" : last.getCreatedAt().format(TIME));
        }
        return vo;
    }

    /**
     * 列表里那一行摘要。
     *
     * <p>媒体消息的 content 是空串（正文不存"[图片]"这种占位，老师端气泡靠 type 渲染）。
     * 直接拿 content 会让列表上的"最后一条"变成空白，看起来像这个群没消息。
     */
    private String previewOf(ContainerMessage m) {
        if (StringUtils.hasText(m.getContent())) {
            return m.getContent();
        }
        Integer t = m.getType();
        if (t == null) {
            return "";
        }
        return switch (t) {
            case ContainerMessage.TYPE_IMAGE -> "[图片]";
            case ContainerMessage.TYPE_VIDEO -> "[视频]";
            case ContainerMessage.TYPE_FILE -> "[文件]";
            default -> "";
        };
    }

    /** 群里的老师人数 */
    private int countTeachers(SubjectContainer c) {
        return (int) nz(memberMapper.selectCount(new LambdaQueryWrapper<ContainerMember>()
                .eq(ContainerMember::getContainerId, c.getId())
                .eq(ContainerMember::getStatus, ContainerMember.STATUS_IN)));
    }

    /**
     * 群里的学生人数 = 本班在读学生 + 用邀请码加进来的其他学生。
     *
     * <p>不用 t_container_member 的行数：学生不在那张表里，只数它会得到"1 人"，
     * 而聊天记录里明明有几十个人在说话。
     */
    private int countStudents(SubjectContainer c) {
        long students = 0;
        if (c.getClassId() != null) {
            students = nz(studentMapper.selectCount(new LambdaQueryWrapper<Student>()
                    .eq(Student::getClassId, c.getClassId())
                    .eq(Student::getStatus, 1)));
        }
        long extra = nz(joinedMapper.selectCount(new LambdaQueryWrapper<StudentContainer>()
                .eq(StudentContainer::getContainerId, c.getId())));
        return (int) (students + extra);
    }

    private static long nz(Long v) {
        return v == null ? 0L : v;
    }

    /**
     * 媒体地址必须是上传接口返回的 {@code /files/...}。
     *
     * <p>和老师端 {@code ContainerMessageServiceImpl.requireUploadedUrl} 同一条规则：
     * 不校验前缀，谁都能在群里挂一个外部链接（可追踪的图、钓鱼页）。
     */
    private String requireUploadedUrl(String raw) {
        String url = raw == null ? "" : raw.trim();
        if (!StringUtils.hasText(url)) {
            throw new BizException(1001, "缺少文件地址");
        }
        if (!url.startsWith("/files/") || url.contains("..") || url.contains("\\")) {
            throw new BizException(1001, "文件地址不合法");
        }
        if (url.length() > 500) {
            throw new BizException(1001, "文件地址太长");
        }
        return url;
    }

    /** 文件名只用于展示，去掉路径分隔符并截断，别让它影响渲染 */
    private String clipName(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String name = raw.trim().replace("/", "").replace("\\", "");
        return name.length() > 128 ? name.substring(0, 128) : name;
    }

    private StudentMessageVO toVO(ContainerMessage m, Long studentId) {
        return toVO(m, studentId, Collections.emptyMap());
    }

    private StudentMessageVO toVO(ContainerMessage m, Long studentId, Map<Long, String> teacherNames) {
        boolean fromStudent = m.getSenderType() != null
                && m.getSenderType() == ContainerMessage.FROM_STUDENT;

        StudentMessageVO vo = new StudentMessageVO();
        vo.setId(m.getId());
        vo.setSenderType(fromStudent ? ContainerMessage.FROM_STUDENT : ContainerMessage.FROM_TEACHER);
        if (fromStudent) {
            vo.setSenderName(StringUtils.hasText(m.getSenderName()) ? m.getSenderName() : "同学");
        } else {
            String name = teacherNames.get(m.getSenderId());
            vo.setSenderName(StringUtils.hasText(name) ? name : "老师");
        }
        vo.setContent(m.getContent());
        vo.setType(m.getType());
        vo.setMediaUrl(m.getMediaUrl());
        vo.setFileName(m.getFileName());
        vo.setFileSize(m.getFileSize());
        vo.setDuration(m.getDuration());
        vo.setTime(m.getCreatedAt() == null ? "" : m.getCreatedAt().format(TIME));
        vo.setCreatedAt(m.getCreatedAt() == null ? null
                : m.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        /* mine 连角色一起比：老师 id=1 和学生 id=1 是两个人 */
        vo.setMine(fromStudent && studentId.equals(m.getSenderId()));
        return vo;
    }
}
