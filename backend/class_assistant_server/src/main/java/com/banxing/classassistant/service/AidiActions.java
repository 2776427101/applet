package com.banxing.classassistant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.banxing.classassistant.common.BizException;
import com.banxing.classassistant.dto.AidiActionVO;
import com.banxing.classassistant.dto.LeaveAuditDTO;
import com.banxing.classassistant.dto.LotteryCreateDTO;
import com.banxing.classassistant.dto.LotteryVO;
import com.banxing.classassistant.dto.MessageSendDTO;
import com.banxing.classassistant.dto.RelayCreateDTO;
import com.banxing.classassistant.dto.ScoreBatchDTO;
import com.banxing.classassistant.dto.TodoCreateDTO;
import com.banxing.classassistant.entity.Exam;
import com.banxing.classassistant.entity.LeaveRequest;
import com.banxing.classassistant.entity.Student;
import com.banxing.classassistant.entity.SubjectContainer;
import com.banxing.classassistant.entity.Todo;
import com.banxing.classassistant.mapper.ExamMapper;
import com.banxing.classassistant.mapper.LeaveRequestMapper;
import com.banxing.classassistant.mapper.StudentMapper;
import com.banxing.classassistant.mapper.SubjectContainerMapper;
import com.banxing.classassistant.mapper.TodoMapper;
import com.banxing.classassistant.util.SubjectUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 乐迪能「动手」的七个动作。
 *
 * <p>分成两步，中间隔着老师的一次点击：
 * <ol>
 *   <li>{@link #preview}：只校验、只解析，把「要动谁、动什么」摊成一张卡，<b>不落库</b></li>
 *   <li>{@link #execute}：老师点确认之后才真正调用业务 Service</li>
 * </ol>
 *
 * <p><b>为什么不让模型直接改</b>：查错了只是看到个错数字，改错了是真的把数据改了。
 * 审批请假、群发消息都没有撤销按钮，一次点错的代价远大于省下的那一下。
 *
 * <p><b>为什么预览和执行各解析一遍</b>：预览到确认之间数据可能变了
 * （待办被别的页面完成了、请假被批过了）。执行时重新查一次、重新校验，
 * 报「已经处理过了」比闷头改一条过期数据强。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AidiActions {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter SECOND = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final StudentMapper studentMapper;
    private final ExamMapper examMapper;
    private final LeaveRequestMapper leaveMapper;
    private final TodoMapper todoMapper;
    private final SubjectContainerMapper containerMapper;

    private final TodoService todoService;
    private final LeaveService leaveService;
    private final ScoreService scoreService;
    private final ContainerMessageService messageService;
    private final RelayService relayService;
    private final LotteryService lotteryService;

    /** 这个工具名是不是写操作。写操作不进 {@link AidiTools}，走预览 + 确认 */
    public boolean isWrite(String name) {
        return switch (name == null ? "" : name) {
            case "create_todo", "complete_todo", "audit_leave", "send_group_message",
                 "create_relay", "create_lottery", "save_scores" -> true;
            default -> false;
        };
    }

    /* ================================================================
       第一步：预览（校验 + 生成确认卡）
       ================================================================ */

    public AidiActionVO preview(String name, Map<String, Object> args, Long classId) {
        return switch (name == null ? "" : name) {
            case "create_todo" -> previewCreateTodo(args);
            case "complete_todo" -> previewCompleteTodo(args, classId);
            case "audit_leave" -> previewAuditLeave(args, classId);
            case "send_group_message" -> previewSendMessage(args, classId);
            case "create_relay" -> previewCreateRelay(args);
            case "create_lottery" -> previewCreateLottery(args, classId);
            case "save_scores" -> previewSaveScores(args, classId);
            default -> throw new BizException(1001, "我不支持这个操作");
        };
    }

    private AidiActionVO previewCreateTodo(Map<String, Object> args) {
        String title = trim(args, "title");
        if (!StringUtils.hasText(title)) {
            throw new BizException(1001, "待办内容不能为空");
        }
        if (title.length() > 128) {
            title = title.substring(0, 128);
        }
        LocalDateTime due = parseTime(AidiTools.str(args, "dueDate"), true);

        Map<String, Object> p = new LinkedHashMap<>();
        p.put("title", title);
        if (due != null) {
            p.put("dueAt", due.format(SECOND));
        }

        String summary = "「" + title + "」";
        if (due != null) {
            summary += " · " + due.format(DATE) + " 到期";
        }
        return action("create_todo", "新增待办", summary, "确认新增", false, p);
    }

    private AidiActionVO previewCompleteTodo(Map<String, Object> args, Long classId) {
        String keyword = trim(args, "title");
        Todo todo = findUndoneTodo(classId, keyword);

        Map<String, Object> p = new LinkedHashMap<>();
        p.put("_todoId", todo.getId());
        p.put("title", todo.getTitle());

        String summary = "「" + todo.getTitle() + "」标为完成";
        if (todo.getDueAt() != null) {
            summary += "（原本 " + todo.getDueAt().format(DATE) + " 到期）";
        }
        return action("complete_todo", "完成待办", summary, "确认完成", false, p);
    }

    private AidiActionVO previewAuditLeave(Map<String, Object> args, Long classId) {
        String name = trim(args, "studentName");
        String decision = trim(args, "decision");
        boolean approve = isApprove(decision);

        Student stu = requireStudent(classId, name);
        LeaveRequest lv = pendingLeave(classId, stu.getId());

        Map<String, Object> p = new LinkedHashMap<>();
        p.put("_leaveId", lv.getId());
        p.put("status", approve ? 1 : 2);
        String remark = trim(args, "remark");
        if (StringUtils.hasText(remark)) {
            p.put("remark", remark);
        }

        String summary = (approve ? "通过 " : "驳回 ") + stu.getName()
                + " 的" + leaveType(lv.getType()) + " " + range(lv.getStartAt(), lv.getEndAt());
        return action("audit_leave", approve ? "通过请假" : "驳回请假", summary,
                approve ? "确认通过" : "确认驳回", true, p);
    }

    private AidiActionVO previewSendMessage(Map<String, Object> args, Long classId) {
        String content = trim(args, "content");
        if (!StringUtils.hasText(content)) {
            throw new BizException(1001, "消息内容不能为空");
        }
        if (content.length() > 500) {
            throw new BizException(1001, "一条消息最多 500 字，现在 " + content.length() + " 字");
        }
        SubjectContainer group = requireGroup(classId);

        Map<String, Object> p = new LinkedHashMap<>();
        p.put("content", content);

        String summary = "发到「" + group.getName() + "」：" + cut(content, 60);
        return action("send_group_message", "群发消息", summary, "确认发送", true, p);
    }

    private AidiActionVO previewCreateRelay(Map<String, Object> args) {
        String title = trim(args, "title");
        if (!StringUtils.hasText(title)) {
            throw new BizException(1001, "接龙主题不能为空");
        }
        if (title.length() > 30) {
            throw new BizException(1001, "接龙主题最多 30 个字");
        }
        String desc = trim(args, "description");
        if (desc != null && desc.length() > 500) {
            throw new BizException(1001, "接龙说明最多 500 个字");
        }
        LocalDateTime deadline = parseTime(AidiTools.str(args, "deadline"), false);

        Map<String, Object> p = new LinkedHashMap<>();
        p.put("title", title);
        if (StringUtils.hasText(desc)) {
            p.put("description", desc);
        }
        if (deadline != null) {
            p.put("deadline", deadline.format(SECOND));
        }

        String summary = "发起接龙「" + title + "」";
        if (deadline != null) {
            summary += " · " + deadline.format(DATETIME) + " 截止";
        }
        return action("create_relay", "发起接龙", summary, "确认发起", false, p);
    }

    private AidiActionVO previewCreateLottery(Map<String, Object> args, Long classId) {
        String kind = trim(args, "kind");
        boolean group = kind != null
                && (kind.contains("group") || kind.contains("分组") || "2".equals(kind));
        int count = asInt(args.get("count"), group ? 4 : 1);

        int pool = studentMapper.selectCount(new LambdaQueryWrapper<Student>()
                .eq(Student::getClassId, classId)
                .eq(Student::getStatus, 1)).intValue();
        if (pool == 0) {
            throw new BizException(1001, "这个班还没有学生，抽不了");
        }

        Map<String, Object> p = new LinkedHashMap<>();
        p.put("kind", group ? 2 : 1);
        if (group) {
            count = Math.max(2, Math.min(20, count));
            p.put("count", count);
        } else {
            count = Math.max(1, Math.min(50, Math.min(count, pool)));
            p.put("count", count);
        }

        String summary = group
                ? "分成 " + count + " 组（候选 " + pool + " 人）"
                : "随机点名 " + count + " 人（候选 " + pool + " 人）";
        return action("create_lottery", group ? "随机分组" : "随机点名", summary, "确认抽签", false, p);
    }

    private AidiActionVO previewSaveScores(Map<String, Object> args, Long classId) {
        Student stu = requireStudent(classId, trim(args, "studentName"));
        Exam exam = requireExam(classId, trim(args, "examName"));

        List<String> subjects = SubjectUtil.split(exam.getSubjects());
        Map<String, BigDecimal> fullScore = SubjectUtil.parseFullScore(exam.getFullScore(), subjects);

        List<Map<String, Object>> rows = maps(args.get("scores"));
        if (rows.isEmpty()) {
            throw new BizException(1001, "没说要录哪几科、多少分");
        }

        List<Map<String, Object>> clean = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> row : rows) {
            String subject = row.get("subject") == null ? "" : String.valueOf(row.get("subject")).trim();
            if (!subjects.contains(subject)) {
                throw new BizException(1001, "「" + exam.getName() + "」没有「" + subject
                        + "」这一科，只有：" + String.join("、", subjects));
            }
            BigDecimal score = asDecimal(row.get("score"));
            if (score == null) {
                throw new BizException(1001, "「" + subject + "」缺一个分数");
            }
            BigDecimal full = fullScore.getOrDefault(subject, new BigDecimal("100"));
            if (score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(full) > 0) {
                throw new BizException(1001, "「" + subject + "」要在 0~"
                        + full.stripTrailingZeros().toPlainString() + " 之间");
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("subject", subject);
            item.put("score", score);
            clean.add(item);

            if (sb.length() > 0) {
                sb.append("、");
            }
            sb.append(subject).append(" ").append(score.stripTrailingZeros().toPlainString());
        }

        Map<String, Object> p = new LinkedHashMap<>();
        p.put("_studentId", stu.getId());
        p.put("_examId", exam.getId());
        p.put("scores", clean);

        String summary = stu.getName() + " · " + exam.getName() + "：" + sb;
        return action("save_scores", "录入成绩", summary, "确认录入", false, p);
    }

    /* ================================================================
       第二步：执行（老师已确认）
       ================================================================ */

    public String execute(String type, Map<String, Object> params, Long classId, Long userId) {
        if (params == null) {
            params = Map.of();
        }
        return switch (type == null ? "" : type) {
            case "create_todo" -> doCreateTodo(params, classId, userId);
            case "complete_todo" -> doCompleteTodo(params, classId, userId);
            case "audit_leave" -> doAuditLeave(params, classId, userId);
            case "send_group_message" -> doSendMessage(params, classId, userId);
            case "create_relay" -> doCreateRelay(params, userId, classId);
            case "create_lottery" -> doCreateLottery(params, userId, classId);
            case "save_scores" -> doSaveScores(params, classId, userId);
            default -> throw new BizException(1001, "我不支持这个操作");
        };
    }

    private String doCreateTodo(Map<String, Object> p, Long classId, Long userId) {
        TodoCreateDTO dto = new TodoCreateDTO();
        dto.setTitle(trim(p, "title"));
        dto.setDueAt(parseTime(AidiTools.str(p, "dueAt"), true));
        todoService.add(userId, classId, dto);

        String out = "已新增待办「" + dto.getTitle() + "」";
        if (dto.getDueAt() != null) {
            out += "，" + dto.getDueAt().format(DATE) + " 到期";
        }
        return out;
    }

    private String doCompleteTodo(Map<String, Object> p, Long classId, Long userId) {
        Long id = asLong(p.get("_todoId"));
        if (id == null) {
            throw new BizException(1001, "这条待办已经找不到了");
        }
        Todo todo = todoMapper.selectById(id);
        /* 归属再查一次：id 是从前端回传的，不能假设它一定是这个班的 */
        if (todo == null || !classId.equals(todo.getClassId())) {
            throw new BizException(1004, "这条待办已经找不到了");
        }
        if (todo.getDone() != null && todo.getDone() == 1) {
            throw new BizException(1001, "「" + todo.getTitle() + "」已经完成了");
        }
        todoService.setDone(userId, id, 1);
        return "已把「" + todo.getTitle() + "」标为完成";
    }

    private String doAuditLeave(Map<String, Object> p, Long classId, Long userId) {
        Long id = asLong(p.get("_leaveId"));
        if (id == null) {
            throw new BizException(1001, "这条请假已经找不到了");
        }
        LeaveRequest lv = leaveMapper.selectById(id);
        if (lv == null || !classId.equals(lv.getClassId())) {
            throw new BizException(1004, "这条请假已经找不到了");
        }
        if (lv.getStatus() == null || lv.getStatus() != 0) {
            throw new BizException(1001, "这条请假已经处理过了");
        }

        LeaveAuditDTO dto = new LeaveAuditDTO();
        dto.setStatus(asInt(p.get("status"), 1) == 2 ? 2 : 1);
        String remark = trim(p, "remark");
        if (StringUtils.hasText(remark)) {
            dto.setRemark(remark);
        }
        leaveService.audit(userId, id, dto);

        Student stu = studentMapper.selectById(lv.getStudentId());
        String who = stu == null ? "该学生" : stu.getName();
        return (dto.getStatus() == 1 ? "已通过 " : "已驳回 ") + who + " 的" + leaveType(lv.getType());
    }

    private String doSendMessage(Map<String, Object> p, Long classId, Long userId) {
        String content = trim(p, "content");
        if (!StringUtils.hasText(content)) {
            throw new BizException(1001, "消息内容不能为空");
        }
        SubjectContainer group = requireGroup(classId);

        MessageSendDTO dto = new MessageSendDTO();
        dto.setContent(content);
        dto.setType(1);
        messageService.send(userId, group.getId(), dto);
        return "已发到「" + group.getName() + "」";
    }

    private String doCreateRelay(Map<String, Object> p, Long userId, Long classId) {
        SubjectContainer group = requireGroup(classId);

        RelayCreateDTO dto = new RelayCreateDTO();
        dto.setTitle(trim(p, "title"));
        dto.setDescription(trim(p, "description"));
        dto.setEntryFormat(1);
        dto.setAllowSelfEdit(true);
        dto.setDeadline(parseTime(AidiTools.str(p, "deadline"), false));

        relayService.create(userId, group.getId(), dto);
        return "已发起接龙「" + dto.getTitle() + "」";
    }

    private String doCreateLottery(Map<String, Object> p, Long userId, Long classId) {
        SubjectContainer group = requireGroup(classId);

        int kind = asInt(p.get("kind"), 1);
        int count = asInt(p.get("count"), 1);

        LotteryCreateDTO dto = new LotteryCreateDTO();
        dto.setKind(kind == 2 ? 2 : 1);
        dto.setSource(1);
        dto.setDedupe(true);
        dto.setPublicResult(true);
        if (kind == 2) {
            dto.setGroupCount(count);
        } else {
            dto.setDrawCount(count);
        }

        LotteryVO created = lotteryService.create(userId, group.getId(), dto);
        LotteryVO drawn = lotteryService.draw(userId, group.getId(), created.getId());

        if (kind == 2) {
            return "已分成 " + (drawn.getGroups() == null ? 0 : drawn.getGroups().size()) + " 组";
        }
        List<String> names = new ArrayList<>();
        if (drawn.getRounds() != null && !drawn.getRounds().isEmpty()) {
            names.addAll(drawn.getRounds().get(drawn.getRounds().size() - 1).getNames());
        }
        return names.isEmpty() ? "已开奖" : "抽到：" + String.join("、", names);
    }

    private String doSaveScores(Map<String, Object> p, Long classId, Long userId) {
        Long studentId = asLong(p.get("_studentId"));
        Long examId = asLong(p.get("_examId"));
        if (studentId == null || examId == null) {
            throw new BizException(1001, "这条成绩记录缺了学生或考试，请重新说一次");
        }

        /* 归属校验：前端回传的 id 不可信，重新确认这两个 id 都在本班 */
        Student stu = studentMapper.selectById(studentId);
        if (stu == null || !classId.equals(stu.getClassId())) {
            throw new BizException(3002, "这个学生不在你的班里");
        }
        Exam exam = examMapper.selectById(examId);
        if (exam == null || !classId.equals(exam.getClassId())) {
            throw new BizException(1004, "这次考试不在你的班里");
        }

        Map<String, BigDecimal> scores = new LinkedHashMap<>();
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> row : maps(p.get("scores"))) {
            String subject = row.get("subject") == null ? "" : String.valueOf(row.get("subject")).trim();
            BigDecimal score = asDecimal(row.get("score"));
            if (!StringUtils.hasText(subject) || score == null) {
                continue;
            }
            scores.put(subject, score);
            if (sb.length() > 0) {
                sb.append("、");
            }
            sb.append(subject).append(" ").append(score.stripTrailingZeros().toPlainString());
        }
        if (scores.isEmpty()) {
            throw new BizException(1001, "没有要录的分数");
        }

        ScoreBatchDTO.Row row = new ScoreBatchDTO.Row();
        row.setStudentId(studentId);
        row.setScores(scores);

        ScoreBatchDTO dto = new ScoreBatchDTO();
        dto.setExamId(examId);
        dto.setRows(List.of(row));

        /* 科目名、分数范围、班级归属全在 batchSave 里再校一遍 ——
           这里不重复写，业务规则只有一个出处 */
        scoreService.batchSave(userId, dto);
        return "已录入 " + stu.getName() + " 的 " + exam.getName() + "：" + sb;
    }

    /* ================================================================
       解析 / 校验
       ================================================================ */

    private Student requireStudent(Long classId, String name) {
        if (!StringUtils.hasText(name)) {
            throw new BizException(1001, "要动哪个学生？说个名字");
        }
        List<Student> hits = studentMapper.selectList(new LambdaQueryWrapper<Student>()
                .eq(Student::getClassId, classId)
                .eq(Student::getStatus, 1)
                .eq(Student::getName, name.trim()));
        if (hits.isEmpty()) {
            hits = studentMapper.selectList(new LambdaQueryWrapper<Student>()
                    .eq(Student::getClassId, classId)
                    .eq(Student::getStatus, 1)
                    .like(Student::getName, name.trim()));
        }
        if (hits.isEmpty()) {
            throw new BizException(1001, "本班没有叫「" + name.trim() + "」的学生");
        }
        if (hits.size() > 1) {
            /* 重名不猜。猜错了就是把分记到别人头上、把假批给别人 */
            throw new BizException(1001, "本班有 " + hits.size() + " 个「" + name.trim()
                    + "」，请说完整姓名或学号");
        }
        return hits.get(0);
    }

    private Exam requireExam(Long classId, String name) {
        if (!StringUtils.hasText(name)) {
            List<Exam> latest = examMapper.selectList(new LambdaQueryWrapper<Exam>()
                    .eq(Exam::getClassId, classId)
                    .orderByDesc(Exam::getExamDate)
                    .orderByDesc(Exam::getId)
                    .last("LIMIT 1"));
            if (latest.isEmpty()) {
                throw new BizException(1001, "这个班还没有考试，先去成绩页新建一次");
            }
            return latest.get(0);
        }
        List<Exam> hits = examMapper.selectList(new LambdaQueryWrapper<Exam>()
                .eq(Exam::getClassId, classId)
                .like(Exam::getName, name.trim())
                .orderByDesc(Exam::getExamDate)
                .orderByDesc(Exam::getId));
        if (hits.isEmpty()) {
            throw new BizException(1001, "本班没有叫「" + name.trim() + "」的考试");
        }
        if (hits.size() > 1) {
            throw new BizException(1001, "有 " + hits.size() + " 次考试都叫「" + name.trim()
                    + "」，请说得更具体些");
        }
        return hits.get(0);
    }

    private LeaveRequest pendingLeave(Long classId, Long studentId) {
        List<LeaveRequest> rows = leaveMapper.selectList(new LambdaQueryWrapper<LeaveRequest>()
                .eq(LeaveRequest::getClassId, classId)
                .eq(LeaveRequest::getStudentId, studentId)
                .eq(LeaveRequest::getStatus, 0)
                .orderByDesc(LeaveRequest::getCreatedAt)
                .orderByDesc(LeaveRequest::getId)
                .last("LIMIT 1"));
        if (rows.isEmpty()) {
            throw new BizException(1001, "这个学生没有待审批的请假");
        }
        return rows.get(0);
    }

    private Todo findUndoneTodo(Long classId, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            throw new BizException(1001, "要完成哪条待办？说个关键词");
        }
        List<Todo> hits = todoMapper.selectList(new LambdaQueryWrapper<Todo>()
                .eq(Todo::getClassId, classId)
                .eq(Todo::getDone, 0)
                .like(Todo::getTitle, keyword.trim())
                .orderByAsc(Todo::getDueAt));
        if (hits.isEmpty()) {
            throw new BizException(1001, "没有找到包含「" + keyword.trim() + "」的未完成待办");
        }
        if (hits.size() > 1) {
            throw new BizException(1001, "有 " + hits.size() + " 条待办都包含「" + keyword.trim()
                    + "」，请说得更具体些");
        }
        return hits.get(0);
    }

    private SubjectContainer requireGroup(Long classId) {
        List<SubjectContainer> rows = containerMapper.selectList(new LambdaQueryWrapper<SubjectContainer>()
                .eq(SubjectContainer::getClassId, classId)
                .eq(SubjectContainer::getStatus, 1)
                .orderByAsc(SubjectContainer::getId));
        if (rows.isEmpty()) {
            throw new BizException(1001, "这个班还没有班级群，先去通讯录里建一个");
        }
        return rows.get(0);
    }

    /* ================================================================
       杂项
       ================================================================ */

    private static AidiActionVO action(String type, String title, String summary,
                                       String confirmText, boolean danger, Map<String, Object> params) {
        AidiActionVO vo = new AidiActionVO();
        vo.setType(type);
        vo.setTitle(title);
        vo.setSummary(summary);
        vo.setConfirmText(confirmText);
        vo.setDanger(danger);
        vo.setParams(params);
        return vo;
    }

    private static boolean isApprove(String decision) {
        if (!StringUtils.hasText(decision)) {
            throw new BizException(1001, "是「通过」还是「驳回」？");
        }
        String d = decision.trim().toLowerCase();
        return d.startsWith("approve") || d.startsWith("pass") || d.contains("通过")
                || "1".equals(d) || d.startsWith("yes");
    }

    private static String trim(Map<String, Object> args, String key) {
        String v = AidiTools.str(args, key);
        return StringUtils.hasText(v) ? v : null;
    }

    /**
     * 解析日期。模型偶尔会写「明天」「周五」——那是它该换算好的事，
     * 这里只认 yyyy-MM-dd 和 yyyy-MM-dd HH:mm，认不出就明确报错让它重来。
     */
    private static LocalDateTime parseTime(String raw, boolean endOfDayIfDateOnly) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String s = raw.trim().replace('/', '-');
        if (s.length() == 10) {
            try {
                LocalDate d = LocalDate.parse(s, DATE);
                return endOfDayIfDateOnly ? d.atTime(23, 59) : d.atStartOfDay();
            } catch (Exception e) {
                throw new BizException(1001, "日期「" + raw + "」看不懂，请用 2026-11-21 这种格式");
            }
        }
        for (DateTimeFormatter f : new DateTimeFormatter[]{DATETIME, SECOND}) {
            try {
                return LocalDateTime.parse(s.length() == 16 ? s : s.substring(0, 19), f);
            } catch (Exception ignored) {
                // 换下一个格式再试
            }
        }
        throw new BizException(1001, "时间「" + raw + "」看不懂，请用 2026-11-21 18:00 这种格式");
    }

    private static int asInt(Object v, int fallback) {
        if (v == null) {
            return fallback;
        }
        try {
            return new BigDecimal(String.valueOf(v)).intValue();
        } catch (Exception e) {
            return fallback;
        }
    }

    private static Long asLong(Object v) {
        if (v == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(v)).longValue();
        } catch (Exception e) {
            return null;
        }
    }

    private static BigDecimal asDecimal(Object v) {
        if (v == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(v).trim());
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> maps(Object v) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (v instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    out.add((Map<String, Object>) m);
                }
            }
        }
        return out;
    }

    private static String cut(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private static String range(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null) {
            return "";
        }
        return from.format(DATETIME) + " → " + to.format(DATETIME);
    }

    private static String leaveType(Integer t) {
        if (t == null) {
            return "请假";
        }
        return switch (t) {
            case 1 -> "病假";
            case 2 -> "事假";
            default -> "请假";
        };
    }
}
