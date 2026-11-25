package com.banxing.classassistant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.banxing.classassistant.common.BizException;
import com.banxing.classassistant.dto.StudentHomeVO;
import com.banxing.classassistant.dto.StudentLeaveCreateDTO;
import com.banxing.classassistant.dto.StudentLeaveVO;
import com.banxing.classassistant.dto.StudentNoticeListVO;
import com.banxing.classassistant.dto.StudentProfileUpdateDTO;
import com.banxing.classassistant.dto.StudentProfileVO;
import com.banxing.classassistant.dto.StudentScoreVO;
import com.banxing.classassistant.dto.StudentSeatVO;
import com.banxing.classassistant.entity.ClassInfo;
import com.banxing.classassistant.entity.DirectMessage;
import com.banxing.classassistant.entity.Exam;
import com.banxing.classassistant.entity.LeaveRequest;
import com.banxing.classassistant.entity.Score;
import com.banxing.classassistant.entity.Seat;
import com.banxing.classassistant.entity.Student;
import com.banxing.classassistant.entity.User;
import com.banxing.classassistant.mapper.ClassMapper;
import com.banxing.classassistant.mapper.DirectMessageMapper;
import com.banxing.classassistant.mapper.ExamMapper;
import com.banxing.classassistant.mapper.LeaveRequestMapper;
import com.banxing.classassistant.mapper.ScoreMapper;
import com.banxing.classassistant.mapper.SeatMapper;
import com.banxing.classassistant.mapper.StudentMapper;
import com.banxing.classassistant.mapper.UserMapper;
import com.banxing.classassistant.util.SubjectUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 学生端业务数据（首页 / 请假 / 成绩 / 座位 / 通知 / 资料）
 *
 * <p><b>这个类的每个方法都只接受 classId + studentId，而且这两个值只能来自 token。</b>
 * 没有一个方法收"要查哪个学生"的参数 —— 一旦收，就得在每个方法里再写一遍
 * "这个学生是不是你"，写漏一个就是越权。把边界画在方法签名上，比画在 if 里可靠。
 *
 * <p>数据来源全是老师端已经在用的那几张表（t_leave_request / t_score / t_seat /
 * t_student），没有为学生端新建业务表。原因很简单：学生看的本来就是同一批数据，
 * 只是视角从"全班"缩到"我自己"。另建一套表意味着同一个事实存两份，
 * 早晚会不一致。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StudentDataService {

    private final StudentMapper studentMapper;
    private final ClassMapper classMapper;
    private final UserMapper userMapper;
    private final LeaveRequestMapper leaveRequestMapper;
    private final ExamMapper examMapper;
    private final ScoreMapper scoreMapper;
    private final SeatMapper seatMapper;
    private final DirectMessageMapper directMessageMapper;
    private final DirectMessageService directMessageService;

    /** 一次最多返回多少条请假记录。一个学期不可能超过这个数 */
    private static final int LEAVE_CAP = 100;

    /** 成绩页最多展示多少次考试。再往前翻已经没人看了 */
    private static final int EXAM_CAP = 20;

    /** 请假一次最长多少天。防手滑把结束日期选成明年 */
    private static final int MAX_LEAVE_DAYS = 30;

    /* ================================================================
       首页
       ================================================================ */

    public StudentHomeVO home(Long classId, Long studentId) {
        /* 每个入口都先过一遍 requireStudent：token 可能是换班之前签的 */
        requireStudent(classId, studentId);
        ClassInfo clazz = classMapper.selectById(classId);

        StudentHomeVO vo = new StudentHomeVO();
        vo.setClassInfo(classInfo(clazz));
        vo.setTerm(term(clazz));

        StudentHomeVO.StatsVO stats = new StudentHomeVO.StatsVO();
        stats.setLeavePending(leaveRequestMapper.selectCount(new LambdaQueryWrapper<LeaveRequest>()
                .eq(LeaveRequest::getStudentId, studentId)
                .eq(LeaveRequest::getStatus, 0)));
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        stats.setLeaveThisMonth(leaveRequestMapper.selectCount(new LambdaQueryWrapper<LeaveRequest>()
                .eq(LeaveRequest::getStudentId, studentId)
                .ge(LeaveRequest::getStartAt, monthStart)));
        stats.setUnread(directMessageService.unreadCount(classId, studentId, DirectMessage.FROM_TEACHER));

        /* 有成绩的考试次数：数的是"我"，不是"全班考过几次"。
           全班考了 5 次、我转学来只赶上 2 次，首页就该写 2 */
        List<Score> mine = scoreMapper.selectList(new LambdaQueryWrapper<Score>()
                .eq(Score::getStudentId, studentId)
                .eq(Score::getClassId, classId));
        stats.setExamCount(mine.stream().map(Score::getExamId).distinct().count());
        vo.setStats(stats);

        vo.setLatestExam(latestExam(classId, studentId, mine));

        /* 首页只要"第几排第几列"，不需要 rows/cols —— 首页那个格子放不下，
           而且座位页会单独请求一次完整的 */
        StudentSeatVO seat = seatOf(classId, studentId);
        if (seat != null && seat.isSeated()) {
            StudentHomeVO.SeatVO brief = new StudentHomeVO.SeatVO();
            brief.setRowNo(seat.getRowNo());
            brief.setColNo(seat.getColNo());
            vo.setSeat(brief);
        }
        return vo;
    }

    /**
     * 最近一次出成绩的考试。
     *
     * <p>只在我有成绩的考试里找，不是"班级最近一次考试" ——
     * 班里刚考完还没录我的分，首页就该继续显示上一次，而不是显示一个空分数。
     */
    private StudentHomeVO.LatestExamVO latestExam(Long classId, Long studentId, List<Score> mine) {
        if (mine.isEmpty()) {
            return null;
        }
        List<Long> examIds = mine.stream().map(Score::getExamId).distinct().toList();
        List<Exam> exams = examMapper.selectBatchIds(examIds);
        exams.sort(Comparator
                .comparing(Exam::getExamDate, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Exam::getId, Comparator.reverseOrder()));
        if (exams.isEmpty()) {
            return null;
        }
        Exam exam = exams.get(0);

        List<String> subjects = SubjectUtil.split(exam.getSubjects());
        Map<String, BigDecimal> fullScore = SubjectUtil.parseFullScore(exam.getFullScore(), subjects);

        Map<Long, BigDecimal> totals = examTotals(exam.getId(), classId);
        BigDecimal total = totals.get(studentId);
        if (total == null) {
            return null;
        }

        StudentHomeVO.LatestExamVO vo = new StudentHomeVO.LatestExamVO();
        vo.setExamId(exam.getId());
        vo.setName(exam.getName());
        vo.setExamDate(exam.getExamDate());
        vo.setTotal(total);
        vo.setFullTotal(sum(fullScore.values()));
        int[] r = rank(totals, studentId);
        vo.setRank(r[0] == 0 ? null : r[0]);
        vo.setClassSize(r[1]);
        return vo;
    }

    /* ================================================================
       请假
       ================================================================ */

    public List<StudentLeaveVO> leaveList(Long classId, Long studentId) {
        requireStudent(classId, studentId);
        List<LeaveRequest> rows = leaveRequestMapper.selectList(new LambdaQueryWrapper<LeaveRequest>()
                .eq(LeaveRequest::getClassId, classId)
                .eq(LeaveRequest::getStudentId, studentId)
                .orderByDesc(LeaveRequest::getCreatedAt)
                .orderByDesc(LeaveRequest::getId)
                .last("LIMIT " + LEAVE_CAP));

        List<StudentLeaveVO> out = new ArrayList<>(rows.size());
        for (LeaveRequest r : rows) {
            out.add(StudentLeaveVO.from(r));
        }
        return out;
    }

    /**
     * 单条请假详情。
     *
     * <p>为什么不让前端从列表里挑：列表有条数上限（100），
     * 一个学期请到第 101 条时详情页就会"找不到"，而这种 bug
     * 只会在快毕业时才暴露。
     */
    public StudentLeaveVO leaveDetail(Long classId, Long studentId, Long id) {
        return StudentLeaveVO.from(ownLeave(classId, studentId, id));
    }

    /**
     * 提交请假。
     *
     * <p>落库状态一律 0（待审批），前端说了不算 —— 学生自己能批自己，
     * 这个模块就没有存在意义了。老师代录的那条路径仍然是"直接已通过"，
     * 因为那是老师用自己的权限替学生登记。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long applyLeave(Long classId, Long studentId, StudentLeaveCreateDTO dto) {
        requireStudent(classId, studentId);

        if (dto == null || dto.getType() == null || dto.getStartAt() == null || dto.getEndAt() == null) {
            throw new BizException(1001, "请假类型和时间都要填");
        }
        if (dto.getType() < 1 || dto.getType() > 3) {
            throw new BizException(1001, "请假类型不对");
        }
        if (!dto.getEndAt().isAfter(dto.getStartAt())) {
            throw new BizException(1001, "结束时间要晚于开始时间");
        }
        long days = ChronoUnit.DAYS.between(
                dto.getStartAt().toLocalDate(), dto.getEndAt().toLocalDate()) + 1;
        if (days > MAX_LEAVE_DAYS) {
            throw new BizException(1001, "一次最多请 " + MAX_LEAVE_DAYS + " 天，更长的找班主任");
        }
        String reason = dto.getReason() == null ? null : dto.getReason().trim();
        if (reason != null && reason.length() > 255) {
            throw new BizException(1001, "请假原因最多 255 个字");
        }

        /* 同一时间段重复提交：多半是网络慢又点了一次。
           不拦的话老师会看到两条一模一样的申请，还得自己判断删哪条 */
        long dup = leaveRequestMapper.selectCount(new LambdaQueryWrapper<LeaveRequest>()
                .eq(LeaveRequest::getStudentId, studentId)
                .eq(LeaveRequest::getStartAt, dto.getStartAt())
                .eq(LeaveRequest::getEndAt, dto.getEndAt())
                .in(LeaveRequest::getStatus, 0, 1));
        if (dup > 0) {
            throw new BizException(1003, "这个时间段已经提交过了");
        }

        LeaveRequest r = new LeaveRequest();
        r.setClassId(classId);
        r.setStudentId(studentId);
        r.setType(dto.getType());
        r.setStartAt(dto.getStartAt());
        r.setEndAt(dto.getEndAt());
        r.setReason(StringUtils.hasText(reason) ? reason : null);
        r.setStatus(0);
        LocalDateTime now = LocalDateTime.now();
        r.setCreatedAt(now);
        r.setUpdatedAt(now);
        leaveRequestMapper.insert(r);
        log.info("学生提交请假：studentId={} id={} {} → {}", studentId, r.getId(),
                dto.getStartAt(), dto.getEndAt());
        return r.getId();
    }

    /**
     * 撤销申请。
     *
     * <p><b>这里是真的删行，不是把状态改成"已撤销"。</b>理由：
     * <ol>
     *   <li>只有 status=0 能撤，这时候老师还没看过，删掉不损失任何审批信息</li>
     *   <li>加一个 status=4 的话，老师端那张表的文案映射里没有 4，
     *       状态列会变成空白 —— 那就得改老师端代码，而这次明确不动老师端</li>
     * </ol>
     * 如果以后要做"撤回留痕"，正确做法是加 4 并同时改两端文案，而不是在这里硬塞。
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancelLeave(Long classId, Long studentId, Long id) {
        LeaveRequest r = ownLeave(classId, studentId, id);
        if (r.getStatus() == null || r.getStatus() != 0) {
            throw new BizException(1003, "班主任已经处理过了，不能撤销");
        }
        leaveRequestMapper.deleteById(id);
        log.info("学生撤销请假：studentId={} id={}", studentId, id);
    }

    /**
     * 销假：人提前回来了，把"已通过"改成"已销假"。
     *
     * <p>不校验时间：请假条常常是事后才销（比如周五的假周一才想起来点），
     * 加一个"必须过期前"的校验只会让学生点不了。
     */
    @Transactional(rollbackFor = Exception.class)
    public void reportLeave(Long classId, Long studentId, Long id) {
        LeaveRequest r = ownLeave(classId, studentId, id);
        if (r.getStatus() == null || r.getStatus() != 1) {
            throw new BizException(1003, "只有已通过的请假才能销假");
        }
        r.setStatus(3);
        r.setUpdatedAt(LocalDateTime.now());
        leaveRequestMapper.updateById(r);
        log.info("学生销假：studentId={} id={}", studentId, id);
    }

    /** 按 id 取自己的请假条。不存在 / 不是自己的 / 不是本班，一律同一句话 */
    private LeaveRequest ownLeave(Long classId, Long studentId, Long id) {
        requireStudent(classId, studentId);
        LeaveRequest r = id == null ? null : leaveRequestMapper.selectById(id);
        if (r == null || !studentId.equals(r.getStudentId()) || !classId.equals(r.getClassId())) {
            /* 不区分"不存在"和"不是你的"：区分了就能被用来探测别人有几条请假 */
            throw new BizException(1004, "没找到这条请假记录");
        }
        return r;
    }

    /* ================================================================
       成绩
       ================================================================ */

    public StudentScoreVO score(Long classId, Long studentId) {
        requireStudent(classId, studentId);

        List<Score> mine = scoreMapper.selectList(new LambdaQueryWrapper<Score>()
                .eq(Score::getStudentId, studentId)
                .eq(Score::getClassId, classId));
        StudentScoreVO vo = new StudentScoreVO();
        vo.setList(Collections.emptyList());
        StudentScoreVO.Summary summary = new StudentScoreVO.Summary();
        vo.setSummary(summary);
        if (mine.isEmpty()) {
            return vo;
        }

        /* 我考的科目 → 考试 id */
        Map<Long, Map<String, BigDecimal>> byExam = new HashMap<>();
        for (Score r : mine) {
            byExam.computeIfAbsent(r.getExamId(), k -> new LinkedHashMap<>())
                    .put(r.getSubject(), r.getScore());
        }

        List<Exam> exams = examMapper.selectBatchIds(byExam.keySet());
        exams.sort(Comparator
                .comparing(Exam::getExamDate, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Exam::getId, Comparator.reverseOrder()));
        if (exams.size() > EXAM_CAP) {
            exams = new ArrayList<>(exams.subList(0, EXAM_CAP));
        }

        /* 一次把这几场考试的全班成绩拉出来，在内存里按考试分堆。
           逐场查就是 N+1：20 场考试 20 条 SQL，页面打开要多等一秒 */
        List<Long> examIds = exams.stream().map(Exam::getId).toList();
        List<Score> all = scoreMapper.selectList(new LambdaQueryWrapper<Score>()
                .eq(Score::getClassId, classId)
                .in(Score::getExamId, examIds));
        Map<Long, Map<Long, BigDecimal>> totalsByExam = new HashMap<>();
        for (Score r : all) {
            if (r.getScore() == null) {
                continue;
            }
            totalsByExam.computeIfAbsent(r.getExamId(), k -> new HashMap<>())
                    .merge(r.getStudentId(), r.getScore(), BigDecimal::add);
        }

        List<StudentScoreVO.ExamScore> list = new ArrayList<>(exams.size());
        Integer best = null;
        for (Exam exam : exams) {
            List<String> subjects = SubjectUtil.split(exam.getSubjects());
            Map<String, BigDecimal> fullScore = SubjectUtil.parseFullScore(exam.getFullScore(), subjects);
            Map<String, BigDecimal> myScores = byExam.getOrDefault(exam.getId(), Map.of());

            StudentScoreVO.ExamScore es = new StudentScoreVO.ExamScore();
            es.setExamId(exam.getId());
            es.setName(exam.getName());
            es.setType(exam.getType());
            es.setExamDate(exam.getExamDate());
            es.setSubjects(subjects);
            es.setFullScore(fullScore);
            es.setScores(myScores);

            /* 总分只累加"我有的那几科"。一科都没录 → null，不是 0。
               0 分和"没录"混在一起，平均分和排名都会被拉低 */
            BigDecimal total = null;
            for (String sub : subjects) {
                BigDecimal v = myScores.get(sub);
                if (v != null) {
                    total = total == null ? v : total.add(v);
                }
            }
            es.setTotal(total);
            es.setFullTotal(sum(fullScore.values()));

            Map<Long, BigDecimal> totals = totalsByExam.getOrDefault(exam.getId(), Map.of());
            int[] r = rank(totals, studentId);
            es.setRank(r[0] == 0 ? null : r[0]);
            es.setClassSize(r[1]);
            es.setClassAvg(avg(totals.values()));

            if (r[0] > 0 && (best == null || r[0] < best)) {
                best = r[0];
            }
            list.add(es);
        }

        summary.setExamCount(list.size());
        summary.setBestRank(best);
        vo.setList(list);
        return vo;
    }

    /* ================================================================
       座位
       ================================================================ */

    public StudentSeatVO seat(Long classId, Long studentId) {
        requireStudent(classId, studentId);
        StudentSeatVO vo = seatOf(classId, studentId);
        return vo == null ? new StudentSeatVO() : vo;
    }

    private StudentSeatVO seatOf(Long classId, Long studentId) {
        Seat seat = seatMapper.selectOne(new LambdaQueryWrapper<Seat>()
                .eq(Seat::getClassId, classId)
                .eq(Seat::getStudentId, studentId)
                .last("LIMIT 1"));
        if (seat == null) {
            return null;
        }
        StudentSeatVO vo = new StudentSeatVO();
        vo.setRowNo(seat.getRowNo());
        vo.setColNo(seat.getColNo());
        vo.setSeated(true);
        /* 教室尺寸取全班座位里最大的行列号。只给数字，不给别人坐哪 */
        List<Seat> all = seatMapper.selectList(new LambdaQueryWrapper<Seat>()
                .eq(Seat::getClassId, classId)
                .select(Seat::getRowNo, Seat::getColNo));
        int maxRow = 0;
        int maxCol = 0;
        for (Seat s : all) {
            if (s.getRowNo() != null) {
                maxRow = Math.max(maxRow, s.getRowNo());
            }
            if (s.getColNo() != null) {
                maxCol = Math.max(maxCol, s.getColNo());
            }
        }
        vo.setRows(maxRow);
        vo.setCols(maxCol);
        return vo;
    }

    /* ================================================================
       通知
       ================================================================ */

    public StudentNoticeListVO notices(Long classId, Long studentId, Integer limit) {
        requireStudent(classId, studentId);
        List<StudentNoticeListVO.StudentNoticeVO> all = new ArrayList<>();

        /* ① 请假的审批结果。0（待审批）不算结果，不放进通知 ——
              学生自己刚提交的那条，他自己知道 */
        List<LeaveRequest> results = leaveRequestMapper.selectList(new LambdaQueryWrapper<LeaveRequest>()
                .eq(LeaveRequest::getStudentId, studentId)
                .in(LeaveRequest::getStatus, 1, 2, 3)
                .orderByDesc(LeaveRequest::getUpdatedAt)
                .orderByDesc(LeaveRequest::getId)
                .last("LIMIT 50"));
        for (LeaveRequest r : results) {
            StudentNoticeListVO.StudentNoticeVO n = new StudentNoticeListVO.StudentNoticeVO();
            n.setKind("leave");
            n.setStatus(r.getStatus());
            n.setTitle(leaveResultTitle(r.getStatus()));
            String desc = leaveTypeText(r.getType()) + " · " + leaveRange(r.getStartAt(), r.getEndAt());
            if (StringUtils.hasText(r.getAuditRemark())) {
                desc = desc + " · " + r.getAuditRemark();
            }
            n.setDesc(desc);
            LocalDateTime at = r.getUpdatedAt() == null ? r.getCreatedAt() : r.getUpdatedAt();
            n.setAtText(timeText(at));
            n.setAtMillis(millis(at));
            n.setLeaveId(r.getId());
            all.add(n);
        }

        /* ② 班主任的私信：只在有未读时出一条汇总，不逐条列。
              逐条列的话，老师连发 10 句就会把请假结果全顶下去 */
        int unread = directMessageService.unreadCount(classId, studentId, DirectMessage.FROM_TEACHER);
        if (unread > 0) {
            DirectMessage last = directMessageMapper.selectOne(new LambdaQueryWrapper<DirectMessage>()
                    .eq(DirectMessage::getClassId, classId)
                    .eq(DirectMessage::getStudentId, studentId)
                    .eq(DirectMessage::getSenderRole, DirectMessage.FROM_TEACHER)
                    .orderByDesc(DirectMessage::getId)
                    .last("LIMIT 1"));
            StudentNoticeListVO.StudentNoticeVO n = new StudentNoticeListVO.StudentNoticeVO();
            n.setKind("message");
            n.setTitle("班主任给你发了 " + unread + " 条消息");
            n.setDesc(last == null || last.getContent() == null ? "" : ellipsis(last.getContent(), 40));
            LocalDateTime at = last == null ? LocalDateTime.now() : last.getCreatedAt();
            n.setAtText(timeText(at));
            n.setAtMillis(millis(at));
            all.add(n);
        }

        /* 统一按时间倒序。两段数据各自有序，合起来不一定有序 */
        all.sort(Comparator.comparingLong(StudentNoticeListVO.StudentNoticeVO::getAtMillis).reversed());

        int cap = (limit == null || limit <= 0) ? 20 : Math.min(limit, 100);
        StudentNoticeListVO vo = new StudentNoticeListVO();
        vo.setTotal(all.size());
        vo.setList(all.size() > cap ? new ArrayList<>(all.subList(0, cap)) : all);
        return vo;
    }

    /* ================================================================
       资料
       ================================================================ */

    public StudentProfileVO profile(Long classId, Long studentId) {
        Student s = requireStudent(classId, studentId);
        ClassInfo clazz = classMapper.selectById(classId);
        StudentProfileVO vo = new StudentProfileVO();
        vo.setId(s.getId());
        vo.setName(s.getName());
        vo.setStudentNo(s.getStudentNo());
        vo.setGender(s.getGender());
        vo.setBirthday(s.getBirthday());
        vo.setClassName(clazz == null ? "" : clazz.getName());
        vo.setTeacherName(teacherName(clazz));
        vo.setDormitory(s.getDormitory());
        vo.setPhone(s.getPhone());
        vo.setWechat(s.getWechat());
        vo.setNation(s.getNation());
        vo.setPoliticalStatus(s.getPoliticalStatus());
        return vo;
    }

    /**
     * 改自己的联系方式。
     *
     * <p>null = 不改，空串 = 清空。这两个必须分开：
     * 只改手机号时前端不会带 dormitory，如果把 null 也当成"清空"，
     * 学生每改一次电话宿舍就没了。
     */
    @Transactional(rollbackFor = Exception.class)
    public StudentProfileVO updateProfile(Long classId, Long studentId, StudentProfileUpdateDTO dto) {
        requireStudent(classId, studentId);
        if (dto == null) {
            return profile(classId, studentId);
        }

        /* 必须用 UpdateWrapper 显式 set，不能用 updateById(实体)。
           原因：MyBatis-Plus 的 updateById 默认忽略 null 字段（NOT_NULL 策略），
           所以「把手机号清空」这个操作根本写不进库 —— 前端显示已清空，
           刷新一下又回来了。这是实测时发现的，不是理论问题。 */
        LambdaUpdateWrapper<Student> uw = new LambdaUpdateWrapper<>();
        uw.eq(Student::getId, studentId);
        boolean any = false;

        if (dto.getPhone() != null) {
            String v = dto.getPhone().trim();
            if (v.length() > 20) {
                throw new BizException(1001, "手机号太长了");
            }
            uw.set(Student::getPhone, v.isEmpty() ? null : v);
            any = true;
        }
        if (dto.getWechat() != null) {
            String v = dto.getWechat().trim();
            if (v.length() > 64) {
                throw new BizException(1001, "微信号太长了");
            }
            uw.set(Student::getWechat, v.isEmpty() ? null : v);
            any = true;
        }
        if (dto.getDormitory() != null) {
            String v = dto.getDormitory().trim();
            if (v.length() > 32) {
                throw new BizException(1001, "宿舍号太长了");
            }
            uw.set(Student::getDormitory, v.isEmpty() ? null : v);
            any = true;
        }

        if (any) {
            uw.set(Student::getUpdatedAt, LocalDateTime.now());
            studentMapper.update(null, uw);
            log.info("学生更新资料：studentId={}", studentId);
        }
        return profile(classId, studentId);
    }

    /* ================================================================
       公共
       ================================================================ */

    /**
     * 取当前登录的学生，同时校验三件事：存在、在读、属于 token 里那个班。
     *
     * <p>为什么每次都查一遍库而不是信 token：token 里的 classId 是登录那一刻的，
     * 学生换班之后旧 token 还带着旧班级。查一次库才能保证"你现在确实在这个班"。
     *
     * <p>包级可见（不是 private）：{@link StudentGroupService} 也要做同一道校验，
     * 复制一份判断逻辑的话，改了一处忘了另一处就是越权。
     */
    Student requireStudent(Long classId, Long studentId) {
        if (classId == null || studentId == null) {
            throw new BizException(1002, "登录已失效，请重新登录");
        }
        Student s = studentMapper.selectById(studentId);
        if (s == null || s.getStatus() == null || s.getStatus() != 1) {
            throw new BizException(1002, "登录已失效，请重新登录");
        }
        if (!classId.equals(s.getClassId())) {
            throw new BizException(1002, "登录信息已过期，请重新登录");
        }
        return s;
    }

    private StudentHomeVO.ClassInfoVO classInfo(ClassInfo clazz) {
        StudentHomeVO.ClassInfoVO vo = new StudentHomeVO.ClassInfoVO();
        if (clazz == null) {
            vo.setName("未绑定班级");
            vo.setTeacherName("");
            vo.setTeacherTitle("班主任");
            return vo;
        }
        vo.setId(clazz.getId());
        vo.setName(clazz.getName());
        vo.setGrade(clazz.getGrade());
        vo.setTeacherName(teacherName(clazz));
        vo.setTeacherTitle("班主任");
        return vo;
    }

    private StudentHomeVO.TermVO term(ClassInfo clazz) {
        StudentHomeVO.TermVO vo = new StudentHomeVO.TermVO();
        if (clazz == null || clazz.getTermStartDate() == null) {
            return vo;
        }
        vo.setTermStartDate(clazz.getTermStartDate());
        /* 和老师端同一套算法（HomeServiceImpl 第 109 行）：开学当天算第 1 周。
           两边必须一致，否则老师和学生在同一天看到的周次不同 */
        long days = ChronoUnit.DAYS.between(clazz.getTermStartDate(), LocalDate.now());
        vo.setWeekNo(days < 0 ? 0 : (int) (days / 7) + 1);
        return vo;
    }

    private String teacherName(ClassInfo clazz) {
        if (clazz == null || clazz.getHeadTeacherId() == null) {
            return "班主任";
        }
        User t = userMapper.selectById(clazz.getHeadTeacherId());
        return t == null || !StringUtils.hasText(t.getName()) ? "班主任" : t.getName();
    }

    /** 某场考试里每个学生的总分。没成绩的人不在 map 里 */
    private Map<Long, BigDecimal> examTotals(Long examId, Long classId) {
        List<Score> rows = scoreMapper.selectList(new LambdaQueryWrapper<Score>()
                .eq(Score::getExamId, examId)
                .eq(Score::getClassId, classId));
        Map<Long, BigDecimal> map = new HashMap<>();
        for (Score r : rows) {
            if (r.getScore() != null) {
                map.merge(r.getStudentId(), r.getScore(), BigDecimal::add);
            }
        }
        return map;
    }

    /**
     * 排名。同分并列（1、1、3），和老师端成绩表用的是同一套规则。
     *
     * @return [我的排名, 参与排名人数]，没成绩时返回 [null, 人数]
     */
    private int[] rank(Map<Long, BigDecimal> totals, Long studentId) {
        int size = totals.size();
        BigDecimal mine = totals.get(studentId);
        if (mine == null) {
            return new int[]{0, size};
        }
        int better = 0;
        for (BigDecimal v : totals.values()) {
            if (v.compareTo(mine) > 0) {
                better++;
            }
        }
        return new int[]{better + 1, size};
    }

    private BigDecimal avg(java.util.Collection<BigDecimal> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal v : values) {
            if (v != null) {
                sum = sum.add(v);
            }
        }
        return sum.divide(new BigDecimal(values.size()), 1, RoundingMode.HALF_UP);
    }

    private BigDecimal sum(java.util.Collection<BigDecimal> values) {
        BigDecimal s = BigDecimal.ZERO;
        if (values != null) {
            for (BigDecimal v : values) {
                if (v != null) {
                    s = s.add(v);
                }
            }
        }
        return s;
    }

    private String leaveTypeText(Integer type) {
        if (type == null) {
            return "请假";
        }
        return switch (type) {
            case 1 -> "病假";
            case 2 -> "事假";
            default -> "其他";
        };
    }

    private String leaveResultTitle(Integer status) {
        if (status == null) {
            return "请假状态更新了";
        }
        return switch (status) {
            case 1 -> "你的请假已通过";
            case 2 -> "你的请假被驳回";
            case 3 -> "已销假";
            default -> "请假状态更新了";
        };
    }

    private String leaveRange(LocalDateTime a, LocalDateTime b) {
        if (a == null || b == null) {
            return "—";
        }
        DateTimeFormatter f = DateTimeFormatter.ofPattern("MM-dd HH:mm");
        return a.format(f) + " → " + b.format(f);
    }

    /** 和老师端 HomeServiceImpl 的 timeText 同一套文案，两端口径要一致 */
    private String timeText(LocalDateTime t) {
        if (t == null) {
            return "";
        }
        LocalDateTime now = LocalDateTime.now();
        long minutes = ChronoUnit.MINUTES.between(t, now);
        if (minutes < 1) {
            return "刚刚";
        }
        if (minutes < 60) {
            return minutes + " 分钟前";
        }
        if (t.toLocalDate().equals(now.toLocalDate())) {
            return "今天 " + t.format(DateTimeFormatter.ofPattern("HH:mm"));
        }
        if (t.toLocalDate().equals(now.toLocalDate().minusDays(1))) {
            return "昨天 " + t.format(DateTimeFormatter.ofPattern("HH:mm"));
        }
        return t.format(DateTimeFormatter.ofPattern("MM-dd"));
    }

    private long millis(LocalDateTime t) {
        if (t == null) {
            return 0L;
        }
        return t.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private String ellipsis(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.replace('\n', ' ').trim();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }
}
