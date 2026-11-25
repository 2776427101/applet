package com.banxing.classassistant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.banxing.classassistant.common.BizException;
import com.banxing.classassistant.dto.DirectMessageThreadVO;
import com.banxing.classassistant.dto.DirectMessageVO;
import com.banxing.classassistant.dto.DirectThreadBriefVO;
import com.banxing.classassistant.dto.DirectThreadRow;
import com.banxing.classassistant.entity.ClassInfo;
import com.banxing.classassistant.entity.DirectMessage;
import com.banxing.classassistant.entity.Student;
import com.banxing.classassistant.entity.User;
import com.banxing.classassistant.mapper.ClassMapper;
import com.banxing.classassistant.mapper.DirectMessageMapper;
import com.banxing.classassistant.mapper.StudentMapper;
import com.banxing.classassistant.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 师生私信
 *
 * <p>线程键是 (classId, studentId)，不是"会话表 + 会话 id"：
 * 一对一没有群名、公告、成员这些属性，单独建会话表只是多一次 join。
 *
 * <p><b>两条越权线，两边各自守一次：</b>
 * <ul>
 *   <li>老师：先按学生反查他的班级，再走 {@code resolveClass} 确认这个班是我的 ——
 *       不接受前端传 classId，否则传别人的班就能读别人的学生</li>
 *   <li>学生：classId / studentId 全部来自 token，请求体里没有这两个字段可用</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DirectMessageService {

    /** 一次最多拉多少条。私信是点对点，翻三个月前的对话意义不大 */
    private static final int MAX_MESSAGES = 100;

    /** 会话列表最多显示多少个学生。超过这个数说明该用搜索而不是列表 */
    private static final int MAX_THREADS = 20;

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final DirectMessageMapper messageMapper;
    private final StudentMapper studentMapper;
    private final ClassMapper classMapper;
    private final UserMapper userMapper;
    private final ClassService classService;

    /* ================================================================
       老师侧
       ================================================================ */

    /**
     * 我的私信会话列表（通讯录顶部那一区）。
     *
     * <p>跨全部班级：老师带两个班时，一班的学生和二班的学生在同一个列表里，
     * 按最近说话时间倒序 —— 老师关心的是"谁刚找过我"，不是"这是哪个班的"。
     *
     * <p>已经离班的学生不进列表：他的历史消息还在库里，但点进去发不出去，
     * 列出来只会让人以为能联系上。
     */
    public List<DirectThreadBriefVO> recentThreads(Long userId) {
        List<Long> classIds = classService.myClassIds(userId);
        if (classIds.isEmpty()) {
            return new ArrayList<>();
        }

        List<DirectThreadRow> rows = messageMapper.selectThreads(classIds, MAX_THREADS);
        if (rows.isEmpty()) {
            return new ArrayList<>();
        }

        /* 两步回表：先按 lastId 取最后一条消息，再按 studentId 取姓名。
           两个 selectBatchIds 各一次查询，比在列表里逐行查省 N-1 次往返 */
        List<Long> lastIds = new ArrayList<>(rows.size());
        List<Long> studentIds = new ArrayList<>(rows.size());
        for (DirectThreadRow r : rows) {
            lastIds.add(r.getLastId());
            studentIds.add(r.getStudentId());
        }

        Map<Long, DirectMessage> lastById = new HashMap<>();
        for (DirectMessage m : messageMapper.selectBatchIds(lastIds)) {
            lastById.put(m.getId(), m);
        }
        Map<Long, Student> stuById = new HashMap<>();
        for (Student s : studentMapper.selectBatchIds(studentIds)) {
            stuById.put(s.getId(), s);
        }

        List<DirectThreadBriefVO> list = new ArrayList<>(rows.size());
        for (DirectThreadRow r : rows) {
            Student s = stuById.get(r.getStudentId());
            DirectMessage last = lastById.get(r.getLastId());
            if (s == null || s.getStatus() == null || s.getStatus() != 1 || last == null) {
                continue;
            }
            DirectThreadBriefVO vo = new DirectThreadBriefVO();
            vo.setStudentId(s.getId());
            vo.setName(s.getName());
            vo.setStudentNo(s.getStudentNo());
            vo.setLastContent(last.getContent());
            vo.setLastTime(last.getCreatedAt() == null ? "" : last.getCreatedAt().format(TIME));
            vo.setLastSenderRole(last.getSenderRole());
            vo.setUnread(r.getUnread() == null ? 0 : r.getUnread());
            list.add(vo);
        }
        return list;
    }

    public DirectMessageThreadVO teacherThread(Long userId, Long studentId) {
        Student s = requireStudent(studentId);
        /* 越权检查在这里：不是我的班就直接 3002，连消息都不查 */
        ClassInfo clazz = classService.resolveClass(userId, s.getClassId());

        DirectMessageThreadVO vo = new DirectMessageThreadVO();
        vo.setTitle(s.getName());
        vo.setSubtitle(s.getStudentNo() == null ? clazz.getName() : "学号 " + s.getStudentNo());
        vo.setMessages(messages(clazz.getId(), studentId));
        vo.setUnread(unreadCount(clazz.getId(), studentId, DirectMessage.FROM_STUDENT));
        return vo;
    }

    public DirectMessageVO sendByTeacher(Long userId, Long studentId, String content) {
        Student s = requireStudent(studentId);
        ClassInfo clazz = classService.resolveClass(userId, s.getClassId());
        return save(clazz.getId(), studentId, userId, DirectMessage.FROM_TEACHER, content);
    }

    /** 老师打开对话，把学生发来的消息标成已读 */
    public int markReadByTeacher(Long userId, Long studentId) {
        Student s = requireStudent(studentId);
        ClassInfo clazz = classService.resolveClass(userId, s.getClassId());
        return markRead(clazz.getId(), studentId, DirectMessage.FROM_STUDENT);
    }

    /* ================================================================
       学生侧
       ================================================================ */

    public DirectMessageThreadVO studentThread(Long classId, Long studentId) {
        Student s = requireStudentInClass(classId, studentId);
        ClassInfo clazz = classMapper.selectById(classId);

        DirectMessageThreadVO vo = new DirectMessageThreadVO();
        vo.setTitle(teacherName(clazz));
        vo.setSubtitle(clazz == null ? "" : clazz.getName());
        vo.setMessages(messages(classId, studentId));
        vo.setUnread(unreadCount(classId, studentId, DirectMessage.FROM_TEACHER));
        return vo;
    }

    public DirectMessageVO sendByStudent(Long classId, Long studentId, String content) {
        requireStudentInClass(classId, studentId);
        ClassInfo clazz = classMapper.selectById(classId);
        Long teacherId = clazz == null ? 0L : clazz.getHeadTeacherId();
        return save(classId, studentId, teacherId, DirectMessage.FROM_STUDENT, content);
    }

    public int markReadByStudent(Long classId, Long studentId) {
        requireStudentInClass(classId, studentId);
        return markRead(classId, studentId, DirectMessage.FROM_TEACHER);
    }

    /* ================================================================
       共用
       ================================================================ */

    /**
     * 未读数：数"对方发给我的、还没读的"。
     *
     * @param readerRole 谁在读：FROM_TEACHER = 老师读，要数学生发来的
     */
    public int unreadCount(Long classId, Long studentId, int readerRole) {
        Long n = messageMapper.selectCount(new LambdaQueryWrapper<DirectMessage>()
                .eq(DirectMessage::getClassId, classId)
                .eq(DirectMessage::getStudentId, studentId)
                .eq(DirectMessage::getSenderRole, readerRole)
                .isNull(DirectMessage::getReadAt));
        return n == null ? 0 : n.intValue();
    }

    private List<DirectMessageVO> messages(Long classId, Long studentId) {
        /* 先按 id 倒序取最近 100 条，再翻回来正序显示。
           直接 asc + limit 拿到的是最早的 100 条，对话一长就永远看不到新消息 */
        List<DirectMessage> rows = messageMapper.selectList(new LambdaQueryWrapper<DirectMessage>()
                .eq(DirectMessage::getClassId, classId)
                .eq(DirectMessage::getStudentId, studentId)
                .orderByDesc(DirectMessage::getId)
                .last("LIMIT " + MAX_MESSAGES));
        Collections.reverse(rows);

        List<DirectMessageVO> list = new ArrayList<>(rows.size());
        for (DirectMessage m : rows) {
            list.add(toVO(m));
        }
        return list;
    }

    private DirectMessageVO save(Long classId, Long studentId, Long teacherId,
                                 int senderRole, String raw) {
        String content = raw == null ? "" : raw.trim();
        if (content.isEmpty()) {
            throw new BizException(1001, "消息不能为空");
        }
        if (content.length() > 500) {
            throw new BizException(1001, "一条消息最多 500 字");
        }

        DirectMessage m = new DirectMessage();
        m.setClassId(classId);
        m.setStudentId(studentId);
        m.setTeacherId(teacherId == null ? 0L : teacherId);
        m.setSenderRole(senderRole);
        m.setContent(content);
        /* 建表时 created_at 有 DEFAULT CURRENT_TIMESTAMP，但 MyBatis 只把 id 回填，
           数据库生成的默认值不会回到这个对象里 —— 不显式赋值的话，
           刚发出去的那条消息 time 是空串，要刷新才出现时间 */
        m.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(m);
        return toVO(m);
    }

    private int markRead(Long classId, Long studentId, int senderRole) {
        return messageMapper.update(null, new LambdaUpdateWrapper<DirectMessage>()
                .eq(DirectMessage::getClassId, classId)
                .eq(DirectMessage::getStudentId, studentId)
                .eq(DirectMessage::getSenderRole, senderRole)
                .isNull(DirectMessage::getReadAt)
                .set(DirectMessage::getReadAt, LocalDateTime.now()));
    }

    /* ================================================================
       校验
       ================================================================ */

    private Student requireStudent(Long studentId) {
        if (studentId == null) {
            throw new BizException(1001, "缺少学生");
        }
        Student s = studentMapper.selectById(studentId);
        if (s == null || s.getStatus() == null || s.getStatus() != 1) {
            throw new BizException(1004, "这个学生已经不在了");
        }
        return s;
    }

    /**
     * 学生侧的身份校验。classId 和 studentId 都来自 token，
     * 但还是要确认这行学生真的属于这个班 —— 学生转班后旧 token 不能继续用。
     */
    private Student requireStudentInClass(Long classId, Long studentId) {
        Student s = requireStudent(studentId);
        if (!s.getClassId().equals(classId)) {
            throw new BizException(1002, "登录信息已过期，请重新登录");
        }
        return s;
    }

    private String teacherName(ClassInfo clazz) {
        if (clazz == null || clazz.getHeadTeacherId() == null) {
            return "班主任";
        }
        User t = userMapper.selectById(clazz.getHeadTeacherId());
        if (t == null || !StringUtils.hasText(t.getName())) {
            return "班主任";
        }
        return t.getName();
    }

    private DirectMessageVO toVO(DirectMessage m) {
        DirectMessageVO vo = new DirectMessageVO();
        vo.setId(m.getId());
        vo.setSenderRole(m.getSenderRole());
        vo.setContent(m.getContent());
        vo.setTime(m.getCreatedAt() == null ? "" : m.getCreatedAt().format(TIME));
        vo.setRead(m.getReadAt() != null);
        return vo;
    }
}
