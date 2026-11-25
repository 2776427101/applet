package com.banxing.classassistant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.banxing.classassistant.entity.Exam;
import com.banxing.classassistant.entity.LeaveRequest;
import com.banxing.classassistant.entity.Score;
import com.banxing.classassistant.entity.Student;
import com.banxing.classassistant.entity.Todo;
import com.banxing.classassistant.mapper.ExamMapper;
import com.banxing.classassistant.mapper.LeaveRequestMapper;
import com.banxing.classassistant.mapper.ScoreMapper;
import com.banxing.classassistant.mapper.StudentMapper;
import com.banxing.classassistant.mapper.TodoMapper;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 乐迪的数据工具：模型能调用的每一个「查库动作」都在这里。
 *
 * <p><b>为什么工具要单独一个类</b>：提示词和 HTTP 那一层（{@link AidiServiceImpl}）
 * 经常要改措辞、改轮数，而查库逻辑要保证只读、只查本班、不抛异常。两者放一起，
 * 改一句话都得担心碰到 SQL。
 *
 * <p><b>三条硬规矩</b>：
 * <ol>
 *   <li>所有查询都带 {@code class_id}。模型能传参数，但班 id 永远由后端注入，
 *       不让模型决定查哪个班 —— 这是权限边界，不是提示词能保证的事。</li>
 *   <li>只读。这里没有任何 insert / update。</li>
 *   <li>出错返回 {@code {"error": ...}}，不抛异常。抛出去会让整轮对话失败，
 *       而模型其实可以看着错误信息跟老师说「这个查不到」。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AidiTools {

    /** 单次工具结果的上限（字符）。名单一次返回 57 人就已经一千多字了，再长纯属烧 token */
    private static final int MAX_RESULT_CHARS = 3500;

    /** 名单最多列多少人 */
    private static final int MAX_STUDENTS = 60;

    /** 成绩最多回溯几次考试 */
    private static final int MAX_EXAMS = 5;

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    /* 只做序列化，不需要 Spring 那套配置，所以用静态实例而不是注入 */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final StudentMapper studentMapper;
    private final ExamMapper examMapper;
    private final ScoreMapper scoreMapper;
    private final LeaveRequestMapper leaveMapper;
    private final TodoMapper todoMapper;

    /**
     * 执行一个工具
     *
     * @param name    工具名（模型给的，可能是幻觉，必须容错）
     * @param args    模型给的参数，值统一按字符串读
     * @param classId 已由 Controller 校验过归属的班级
     * @return JSON 字符串，直接塞回给模型
     */
    public String run(String name, Map<String, Object> args, Long classId) {
        try {
            String result = switch (name == null ? "" : name) {
                case "class_overview" -> overview(classId);
                case "list_students" -> students(classId, str(args, "keyword"));
                case "student_scores" -> studentScores(classId, str(args, "studentName"));
                case "exam_summary" -> examSummary(classId, str(args, "examName"));
                case "list_exams" -> exams(classId);
                case "leave_records" -> leaves(classId, str(args, "status"));
                case "todo_items" -> todos(classId, str(args, "done"));
                default -> "{\"error\":\"没有这个工具，只能用 class_overview / list_students / "
                        + "student_scores / exam_summary / list_exams / leave_records / todo_items\"}";
            };
            /* 超长直接截断并标注：截断总比让上游 400 整轮失败强 */
            if (result.length() > MAX_RESULT_CHARS) {
                result = result.substring(0, MAX_RESULT_CHARS) + "…(结果过长已截断)";
            }
            return result;
        } catch (Exception e) {
            log.warn("乐迪工具 {} 执行失败，classId={}", name, classId, e);
            return "{\"error\":\"查询失败，请换个问法或稍后再试\"}";
        }
    }

    /* ================================================================
       1. 班级概况
       ================================================================ */

    private String overview(Long classId) {
        List<Student> all = studentMapper.selectList(new LambdaQueryWrapper<Student>()
                .eq(Student::getClassId, classId));

        int inSchool = 0;
        int male = 0;
        int female = 0;
        for (Student s : all) {
            if (s.getStatus() == null || s.getStatus() != 1) {
                continue;
            }
            inSchool++;
            if (s.getGender() != null && s.getGender() == 1) {
                male++;
            } else if (s.getGender() != null && s.getGender() == 2) {
                female++;
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("studentCount", inSchool);
        out.put("male", male);
        out.put("female", female);
        out.put("leftCount", all.size() - inSchool);

        Exam latest = latestExam(classId);
        if (latest != null) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("name", latest.getName());
            e.put("date", latest.getExamDate() == null ? null : latest.getExamDate().format(DATE));
            out.put("latestExam", e);
        }

        out.put("pendingLeave", leaveMapper.selectCount(new LambdaQueryWrapper<LeaveRequest>()
                .eq(LeaveRequest::getClassId, classId)
                .eq(LeaveRequest::getStatus, 0)));
        out.put("undoneTodo", todoMapper.selectCount(new LambdaQueryWrapper<Todo>()
                .eq(Todo::getClassId, classId)
                .eq(Todo::getDone, 0)));
        out.put("today", LocalDate.now().format(DATE));
        return json(out);
    }

    /* ================================================================
       2. 学生名单
       ================================================================ */

    private String students(Long classId, String keyword) {
        LambdaQueryWrapper<Student> q = new LambdaQueryWrapper<Student>()
                .eq(Student::getClassId, classId)
                .eq(Student::getStatus, 1)
                .orderByAsc(Student::getStudentNo)
                .orderByAsc(Student::getId);
        if (keyword != null && !keyword.isBlank()) {
            String k = keyword.trim();
            q.and(w -> w.like(Student::getName, k).or().like(Student::getStudentNo, k));
        }
        List<Student> rows = studentMapper.selectList(q);

        List<Map<String, Object>> list = new ArrayList<>();
        for (Student s : rows) {
            if (list.size() >= MAX_STUDENTS) {
                break;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", s.getName());
            m.put("no", s.getStudentNo());
            m.put("gender", genderText(s.getGender()));
            list.add(m);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", rows.size());
        out.put("list", list);
        if (rows.size() > list.size()) {
            out.put("note", "只列出前 " + list.size() + " 人");
        }
        return json(out);
    }

    /* ================================================================
       3. 某个学生的成绩
       ================================================================ */

    private String studentScores(Long classId, String name) {
        if (name == null || name.isBlank()) {
            return "{\"error\":\"需要学生姓名\"}";
        }
        String keyword = name.trim();

        /* 先精确匹配，再模糊。顺序不能反：
           「李」模糊会命中一堆人，而老师打的通常就是完整姓名 */
        List<Student> hits = studentMapper.selectList(new LambdaQueryWrapper<Student>()
                .eq(Student::getClassId, classId)
                .eq(Student::getStatus, 1)
                .eq(Student::getName, keyword));

        if (hits.isEmpty()) {
            hits = studentMapper.selectList(new LambdaQueryWrapper<Student>()
                    .eq(Student::getClassId, classId)
                    .eq(Student::getStatus, 1)
                    .like(Student::getName, keyword));
        }
        if (hits.isEmpty()) {
            return "{\"error\":\"本班没有叫「" + keyword + "」的学生\"}";
        }
        if (hits.size() > 1) {
            /* 重名不猜。猜错了就是把别人家的成绩念给老师听 */
            List<String> names = new ArrayList<>();
            for (Student s : hits) {
                names.add(s.getStudentNo() == null ? s.getName() : s.getName() + "（" + s.getStudentNo() + "）");
            }
            return json(Map.of("error", "有 " + hits.size() + " 个学生匹配，请让老师指定学号",
                    "candidates", names));
        }

        Student stu = hits.get(0);
        List<Exam> exams = examMapper.selectList(new LambdaQueryWrapper<Exam>()
                .eq(Exam::getClassId, classId)
                .orderByDesc(Exam::getExamDate)
                .orderByDesc(Exam::getId)
                .last("LIMIT " + MAX_EXAMS));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Exam e : exams) {
            List<Score> scores = scoreMapper.selectList(new LambdaQueryWrapper<Score>()
                    .eq(Score::getExamId, e.getId())
                    .eq(Score::getClassId, classId));

            Map<String, Object> subjects = new LinkedHashMap<>();
            BigDecimal myTotal = BigDecimal.ZERO;
            boolean hasScore = false;

            /* 先把全班的分数按人汇总，自己的排名要用它 */
            Map<Long, BigDecimal> totals = new HashMap<>();
            for (Score s : scores) {
                if (s.getScore() == null) {
                    continue;
                }
                totals.merge(s.getStudentId(), s.getScore(), BigDecimal::add);
                if (s.getStudentId().equals(stu.getId())) {
                    subjects.put(s.getSubject(), num(s.getScore()));
                    myTotal = myTotal.add(s.getScore());
                    hasScore = true;
                }
            }
            /* 这次考试没录到这个人（缺考或没录入）——不编 0 分，整条跳过 */
            if (!hasScore) {
                continue;
            }

            int rank = 1;
            BigDecimal sum = BigDecimal.ZERO;
            for (BigDecimal t : totals.values()) {
                sum = sum.add(t);
                if (t.compareTo(myTotal) > 0) {
                    rank++;
                }
            }

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("exam", e.getName());
            m.put("date", e.getExamDate() == null ? null : e.getExamDate().format(DATE));
            m.put("scores", subjects);
            m.put("total", num(myTotal));
            m.put("rank", rank);
            m.put("classSize", totals.size());
            m.put("classAvg", avg(sum, totals.size()));
            rows.add(m);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("student", stu.getName());
        out.put("no", stu.getStudentNo());
        out.put("exams", rows);
        if (rows.isEmpty()) {
            out.put("note", "这个学生还没有任何成绩记录");
        }
        return json(out);
    }

    /* ================================================================
       4. 考试列表（录入成绩之前模型要靠它确认是哪次考试）
       ================================================================ */

    private String exams(Long classId) {
        List<Exam> rows = examMapper.selectList(new LambdaQueryWrapper<Exam>()
                .eq(Exam::getClassId, classId)
                .orderByDesc(Exam::getExamDate)
                .orderByDesc(Exam::getId)
                .last("LIMIT 10"));

        List<Map<String, Object>> list = new ArrayList<>();
        for (Exam e : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", e.getName());
            m.put("date", e.getExamDate() == null ? null : e.getExamDate().format(DATE));
            m.put("subjects", e.getSubjects());
            list.add(m);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", list.size());
        out.put("list", list);
        if (list.isEmpty()) {
            out.put("note", "这个班还没有考试，录成绩之前要先在成绩页新建一次考试");
        }
        return json(out);
    }

    /* ================================================================
       5. 某次考试的整体情况
       ================================================================ */

    private String examSummary(Long classId, String examName) {
        Exam exam = null;
        if (examName != null && !examName.isBlank()) {
            List<Exam> hits = examMapper.selectList(new LambdaQueryWrapper<Exam>()
                    .eq(Exam::getClassId, classId)
                    .like(Exam::getName, examName.trim())
                    .orderByDesc(Exam::getExamDate)
                    .orderByDesc(Exam::getId));
            if (hits.isEmpty()) {
                return "{\"error\":\"本班没有叫「" + examName.trim() + "」的考试\"}";
            }
            exam = hits.get(0);
        } else {
            exam = latestExam(classId);
        }
        if (exam == null) {
            return "{\"error\":\"这个班还没有考试记录\"}";
        }

        List<Score> scores = scoreMapper.selectList(new LambdaQueryWrapper<Score>()
                .eq(Score::getExamId, exam.getId())
                .eq(Score::getClassId, classId));

        Map<Long, BigDecimal> totals = new HashMap<>();
        for (Score s : scores) {
            if (s.getScore() == null) {
                continue;
            }
            totals.merge(s.getStudentId(), s.getScore(), BigDecimal::add);
        }

        /* 科目平均和单科最高分一起算：老师问「数学最高多少」很常见，
           只给平均的话模型只能回「查不到」 */
        Map<String, BigDecimal> subjSum = new LinkedHashMap<>();
        Map<String, Integer> subjCnt = new LinkedHashMap<>();
        Map<String, BigDecimal> subjMax = new LinkedHashMap<>();
        for (Score s : scores) {
            if (s.getScore() == null || s.getSubject() == null) {
                continue;
            }
            subjSum.merge(s.getSubject(), s.getScore(), BigDecimal::add);
            subjCnt.merge(s.getSubject(), 1, Integer::sum);
            BigDecimal old = subjMax.get(s.getSubject());
            if (old == null || s.getScore().compareTo(old) > 0) {
                subjMax.put(s.getSubject(), s.getScore());
            }
        }

        BigDecimal sum = BigDecimal.ZERO;
        BigDecimal max = null;
        for (BigDecimal t : totals.values()) {
            sum = sum.add(t);
            if (max == null || t.compareTo(max) > 0) {
                max = t;
            }
        }

        /* 前 5 名：把 id → 总分排序，再查名字 */
        List<Map.Entry<Long, BigDecimal>> sorted = new ArrayList<>(totals.entrySet());
        sorted.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        List<Map<String, Object>> top = new ArrayList<>();
        List<Long> topIds = new ArrayList<>();
        for (int i = 0; i < Math.min(5, sorted.size()); i++) {
            topIds.add(sorted.get(i).getKey());
        }
        Map<Long, String> nameById = names(classId, topIds);
        for (int i = 0; i < topIds.size(); i++) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("rank", i + 1);
            m.put("name", nameOrAlert(nameById, topIds.get(i), classId));
            m.put("total", num(sorted.get(i).getValue()));
            top.add(m);
        }

        Map<String, Object> subjectAvg = new LinkedHashMap<>();
        Map<String, Object> subjectMax = new LinkedHashMap<>();
        for (String subject : subjSum.keySet()) {
            subjectAvg.put(subject, avg(subjSum.get(subject), subjCnt.getOrDefault(subject, 0)));
            subjectMax.put(subject, num(subjMax.get(subject)));
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("exam", exam.getName());
        out.put("date", exam.getExamDate() == null ? null : exam.getExamDate().format(DATE));
        out.put("subjects", exam.getSubjects());
        out.put("classSize", totals.size());
        out.put("avgTotal", avg(sum, totals.size()));
        out.put("maxTotal", max == null ? null : num(max));
        out.put("subjectAvg", subjectAvg);
        out.put("subjectMax", subjectMax);
        out.put("top", top);
        return json(out);
    }

    /* ================================================================
       6. 请假记录
       ================================================================ */

    private String leaves(Long classId, String status) {
        LambdaQueryWrapper<LeaveRequest> q = new LambdaQueryWrapper<LeaveRequest>()
                .eq(LeaveRequest::getClassId, classId);

        Integer st = leaveStatus(status);
        if (st != null) {
            q.eq(LeaveRequest::getStatus, st);
        }
        q.orderByDesc(LeaveRequest::getCreatedAt).orderByDesc(LeaveRequest::getId).last("LIMIT 10");
        List<LeaveRequest> rows = leaveMapper.selectList(q);

        List<Long> ids = new ArrayList<>();
        for (LeaveRequest r : rows) {
            ids.add(r.getStudentId());
        }
        Map<Long, String> nameById = names(classId, ids);

        List<Map<String, Object>> list = new ArrayList<>();
        for (LeaveRequest r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", nameOrAlert(nameById, r.getStudentId(), classId));
            m.put("type", leaveType(r.getType()));
            m.put("from", fmt(r.getStartAt()));
            m.put("to", fmt(r.getEndAt()));
            m.put("status", leaveStatusText(r.getStatus()));
            if (r.getReason() != null && !r.getReason().isBlank()) {
                m.put("reason", r.getReason());
            }
            list.add(m);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", list.size());
        out.put("list", list);
        if (list.isEmpty()) {
            out.put("note", "没有符合条件的请假记录");
        }
        return json(out);
    }

    /* ================================================================
       7. 待办
       ================================================================ */

    private String todos(Long classId, String done) {
        LambdaQueryWrapper<Todo> q = new LambdaQueryWrapper<Todo>()
                .eq(Todo::getClassId, classId);
        if ("done".equalsIgnoreCase(safe(done)) || "1".equals(safe(done))) {
            q.eq(Todo::getDone, 1);
        } else if ("undone".equalsIgnoreCase(safe(done)) || "pending".equalsIgnoreCase(safe(done))
                || "0".equals(safe(done))) {
            q.eq(Todo::getDone, 0);
        }
        /* 未完成的按到期时间升序（越早越急），空的到期时间排最后 */
        q.orderByAsc(Todo::getDone).orderByAsc(Todo::getDueAt).last("LIMIT 10");
        List<Todo> rows = todoMapper.selectList(q);

        List<Map<String, Object>> list = new ArrayList<>();
        for (Todo t : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("title", t.getTitle());
            m.put("due", t.getDueAt() == null ? null : t.getDueAt().format(DATE));
            m.put("done", t.getDone() != null && t.getDone() == 1);
            list.add(m);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", list.size());
        out.put("list", list);
        if (list.isEmpty()) {
            out.put("note", "没有符合条件的待办");
        }
        return json(out);
    }

    /* ================================================================
       小工具
       ================================================================ */

    private Exam latestExam(Long classId) {
        List<Exam> rows = examMapper.selectList(new LambdaQueryWrapper<Exam>()
                .eq(Exam::getClassId, classId)
                .orderByDesc(Exam::getExamDate)
                .orderByDesc(Exam::getId)
                .last("LIMIT 1"));
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** id → 姓名。一次 selectBatchIds 就够，不要 N+1 */
    /**
     * 取学生姓名。查不到时**不返回「未知」**。
     *
     * <p>「未知」和「没录成绩」在界面上长得一模一样，之前正因为这个，一张表里
     * 47 个学生的引用全是孤儿（id 1~47，人早就不在了）却一个月没人发现。
     * 这里改成明显的异常文案 + 一条 WARN 日志，宁可难看也不要悄悄糊过去。
     */
    private String nameOrAlert(Map<Long, String> nameById, Long studentId, Long classId) {
        String name = nameById.get(studentId);
        if (name != null && !name.isBlank()) {
            return name;
        }
        log.warn("乐迪查到一个不在班里的 student_id={}（classId={}），数据可能有孤儿引用", studentId, classId);
        return "（数据异常：学生不存在）";
    }

    private Map<Long, String> names(Long classId, List<Long> ids) {
        Map<Long, String> map = new HashMap<>();
        List<Long> clean = new ArrayList<>();
        for (Long id : ids) {
            if (id != null && !clean.contains(id)) {
                clean.add(id);
            }
        }
        if (clean.isEmpty()) {
            return map;
        }
        for (Student s : studentMapper.selectBatchIds(clean)) {
            /* 再校一次班：selectBatchIds 不带 class_id，理论上查不到外班的人，
               但模型能触发这条路径，多一道判断不亏 */
            if (classId.equals(s.getClassId())) {
                map.put(s.getId(), s.getName());
            }
        }
        return map;
    }

    private static String json(Object o) {
        try {
            return MAPPER.writeValueAsString(o);
        } catch (Exception e) {
            return "{\"error\":\"结果序列化失败\"}";
        }
    }

    private static String num(BigDecimal v) {
        if (v == null) {
            return null;
        }
        return v.setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static String avg(BigDecimal sum, int count) {
        if (count <= 0 || sum == null) {
            return null;
        }
        return sum.divide(BigDecimal.valueOf(count), 1, RoundingMode.HALF_UP).toPlainString();
    }

    private static String fmt(LocalDateTime t) {
        return t == null ? null : t.format(DATETIME);
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    /** 从模型给的参数里取一个字符串。参数值类型不确定（数字、布尔都可能），统一转字符串 */
    static String str(Map<String, Object> args, String key) {
        Object v = args == null ? null : args.get(key);
        return v == null ? null : String.valueOf(v).trim();
    }

    private static String genderText(Integer g) {
        if (g == null) {
            return "未知";
        }
        return switch (g) {
            case 1 -> "男";
            case 2 -> "女";
            default -> "未知";
        };
    }

    private static String leaveType(Integer t) {
        if (t == null) {
            return "请假";
        }
        return switch (t) {
            case 1 -> "病假";
            case 2 -> "事假";
            default -> "其他";
        };
    }

    private static String leaveStatusText(Integer s) {
        if (s == null) {
            return "未知";
        }
        return switch (s) {
            case 0 -> "待审批";
            case 1 -> "已通过";
            case 2 -> "已驳回";
            case 3 -> "已销假";
            default -> "未知";
        };
    }

    private static Integer leaveStatus(String s) {
        String v = safe(s).toLowerCase();
        return switch (v) {
            case "pending", "0", "待审批" -> 0;
            case "approved", "1", "已通过" -> 1;
            case "rejected", "2", "已驳回" -> 2;
            case "returned", "3", "已销假" -> 3;
            default -> null;
        };
    }
}
