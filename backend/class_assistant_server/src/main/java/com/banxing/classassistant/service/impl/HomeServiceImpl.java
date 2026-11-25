package com.banxing.classassistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.banxing.classassistant.dto.HomeNoticeListVO;
import com.banxing.classassistant.dto.HomeNoticeVO;
import com.banxing.classassistant.dto.HomeOverviewVO;
import com.banxing.classassistant.entity.ClassInfo;
import com.banxing.classassistant.entity.LeaveRequest;
import com.banxing.classassistant.entity.Student;
import com.banxing.classassistant.entity.Todo;
import com.banxing.classassistant.entity.User;
import com.banxing.classassistant.mapper.LeaveRequestMapper;
import com.banxing.classassistant.mapper.StudentMapper;
import com.banxing.classassistant.mapper.TodoMapper;
import com.banxing.classassistant.service.ClassService;
import com.banxing.classassistant.service.HomeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 教师首页业务实现
 * 首页只有读操作，不涉及事务
 */
//Spring 注解，把这个类注册成一个 Bean 交给 Spring 容器管理，其他类（如 Controller）就能通过@Autowired直接注入使用，不用自己new
@Service
//编译时自动生成一个构造函数，参数是所有 final 字段。
@RequiredArgsConstructor
public class HomeServiceImpl implements HomeService {

    private final ClassService classService;
    private final StudentMapper studentMapper;
    private final LeaveRequestMapper leaveRequestMapper;
    private final TodoMapper todoMapper;

    @Override
    public HomeOverviewVO overview(Long userId, Long classId) {
        // 第一步：校验身份 + 解析班级。
        // resolveClass 会同时做两件事：role 不是 1 抛 3001；classId 不是自己的班抛 3002。
        // 传 null 时退化成"我的第一个班"，只为兼容还没传 classId 的旧前端
        User teacher = classService.requireTeacher(userId);
        ClassInfo clazz = classService.resolveClass(userId, classId);

        HomeOverviewVO vo = new HomeOverviewVO();

        // 没带班：返回空壳，前端可以正常渲染（数字都是 0），不用报错
        if (clazz == null) {
            vo.setStats(new HomeOverviewVO.StatsVO());
            vo.setTerm(new HomeOverviewVO.TermVO());
            return vo;
        }
        // 第三步：四个统计，各查一次 count
        // 今日请假：今天"开始"的、且已通过的记录数
        Long leaveToday = leaveRequestMapper.selectCount(
                new LambdaQueryWrapper<LeaveRequest>()
                        .eq(LeaveRequest::getClassId, clazz.getId()) // 班级相同
                        .eq(LeaveRequest::getStatus, 1) // 已通过
                        // apply 用来拼原生 SQL 片段，这里判断"开始时间在今天之内"
                        .apply("DATE(start_at) = CURDATE()"));
        // 今日生日：生日"月+日"等于今天，且在读
        Long birthdayToday = studentMapper.selectCount(
                new LambdaQueryWrapper<Student>()
                        .eq(Student::getClassId, clazz.getId()) // 班级相同
                        .eq(Student::getStatus, 1) // 在读
                        //生日在今天之内
                        .apply("MONTH(birthday) = MONTH(CURDATE()) AND DAY(birthday) = DAY(CURDATE())"));
        // 学生总数：本班在读
        Long studentCount = studentMapper.selectCount(
                new LambdaQueryWrapper<Student>()
                        .eq(Student::getClassId, clazz.getId())
                        .eq(Student::getStatus, 1));
        // 待办未完成：属于当前老师、属于当前班级，且done=0。
        // 加上 class_id 是必须的：不带的话，切到另一个班时待办数字不会变，
        // 老师会以为"这个班的待办怎么还是那几条"
        Long todoUndone = todoMapper.selectCount(
                new LambdaQueryWrapper<Todo>()
                        .eq(Todo::getTeacherId, userId)
                        .eq(Todo::getClassId, clazz.getId())
                        .eq(Todo::getDone, 0));
        // 第四步：组装统计结果
        HomeOverviewVO.StatsVO stats = new HomeOverviewVO.StatsVO();
        stats.setLeaveToday(leaveToday == null ? 0 : leaveToday);
        stats.setBirthdayToday(birthdayToday == null ? 0 : birthdayToday);
        stats.setStudentCount(studentCount == null ? 0 : studentCount);
        stats.setTodoUndone(todoUndone == null ? 0 : todoUndone);
        vo.setStats(stats);
        // 第五步：组装班级信息
        HomeOverviewVO.ClassInfoVO classInfo = new HomeOverviewVO.ClassInfoVO();
        classInfo.setId(clazz.getId());
        classInfo.setName(clazz.getName());
        classInfo.setGrade(clazz.getGrade());
        classInfo.setTeacherName(teacher.getName());
        classInfo.setTeacherTitle("班主任");
        vo.setClassInfo(classInfo);
        // 第六步：算第几周。公式：(今天 - 学期第一天) / 7 + 1，向下取整
        HomeOverviewVO.TermVO term = new HomeOverviewVO.TermVO();
        if (clazz.getTermStartDate() != null) {
            long days = ChronoUnit.DAYS.between(clazz.getTermStartDate(), LocalDate.now());
            term.setWeekNo((int) (days / 7) + 1);
            term.setTermStartDate(clazz.getTermStartDate());
        }
        vo.setTerm(term);

        return vo;
    }

    /* 时间文案用到的几个格式，做成常量避免每次 format 都重新编译 pattern */
    private static final DateTimeFormatter F_HM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter F_MDHM = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    private static final DateTimeFormatter F_MD = DateTimeFormatter.ofPattern("MM-dd");
    private static final DateTimeFormatter F_YMD = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /* 单个来源最多捞多少条。total 是"真实条数"，最多准到 3×100；
       超过这个量级说明老师积压了一学期的活，界面该显示"99+"了 */
    private static final int QUERY_CAP = 100;

    @Override
    public HomeNoticeListVO notices(Long userId, Long classId, Integer limit) {
        // 和 overview 一样先做权限校验：不是老师抛 3001
        classService.requireTeacher(userId);

        HomeNoticeListVO vo = new HomeNoticeListVO();
        List<HomeNoticeVO> all = new ArrayList<>();

        /* 不带 classId = 我的所有班。
           通知和首页数字不是一回事：数字（多少学生、几节课）看的是"当前这个班"，
           但"有人请假等我批"跨班也得知道 —— 老师带两个班时，
           切到二班才看见一班的请假申请，等于没提醒。 */
        List<ClassInfo> classes;
        if (classId == null) {
            classes = classService.myClasses(userId);
        } else {
            // 带了班就按班来，顺便校验这个班是不是他的（越权抛 3002）
            ClassInfo one = classService.resolveClass(userId, classId);
            classes = one == null ? List.of() : List.of(one);
        }

        // 没带班且一个班都没有：返回空列表而不是报错，首页那个框显示"暂无新通知"就行
        if (classes.isEmpty()) {
            vo.setTotal(0);
            vo.setList(all);
            return vo;
        }

        List<Long> classIds = classes.stream().map(ClassInfo::getId).toList();
        Map<Long, String> classNames = new HashMap<>();
        for (ClassInfo c : classes) {
            classNames.put(c.getId(), c.getName());
        }

        // ① 待审批请假 —— 唯一"别人在等你"的通知，排最前
        List<LeaveRequest> pending = leaveRequestMapper.selectList(
                new LambdaQueryWrapper<LeaveRequest>()
                        .in(LeaveRequest::getClassId, classIds)
                        .eq(LeaveRequest::getStatus, 0)
                        .orderByDesc(LeaveRequest::getCreatedAt)
                        .last("LIMIT " + QUERY_CAP));
        if (!pending.isEmpty()) {
            // 一次查完所有涉及的学生，不要在循环里逐条 selectById
            List<Long> ids = pending.stream()
                    .map(LeaveRequest::getStudentId)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .toList();
            Map<Long, String> names = studentNames(ids);
            for (LeaveRequest r : pending) {
                HomeNoticeVO n = new HomeNoticeVO();
                n.setKind("leave");
                n.setTitle(names.getOrDefault(r.getStudentId(), "有学生") + " 提交了请假申请");
                n.setDesc(leaveTypeText(r.getType()) + " · " + leaveRange(r.getStartAt(), r.getEndAt()));
                n.setAtText(timeText(r.getCreatedAt()));
                n.setAtMillis(millis(r.getCreatedAt()));
                n.setStudentId(r.getStudentId());
                n.setLeaveId(r.getId());
                n.setClassId(r.getClassId());
                n.setClassName(classNames.get(r.getClassId()));
                all.add(n);
            }
        }

        // ② 今天到期或已经逾期的待办。排序用 dueAt 升序 —— 越早到期越急
        LocalDateTime endOfToday = LocalDate.now().atTime(23, 59, 59);
        List<Todo> todos = todoMapper.selectList(
                new LambdaQueryWrapper<Todo>()
                        .eq(Todo::getTeacherId, userId)
                        .in(Todo::getClassId, classIds)
                        .eq(Todo::getDone, 0)
                        .isNotNull(Todo::getDueAt)
                        .le(Todo::getDueAt, endOfToday)
                        .orderByAsc(Todo::getDueAt)
                        .last("LIMIT " + QUERY_CAP));
        for (Todo t : todos) {
            long overdue = ChronoUnit.DAYS.between(t.getDueAt().toLocalDate(), LocalDate.now());
            HomeNoticeVO n = new HomeNoticeVO();
            n.setKind("todo");
            n.setTitle("待办：" + t.getTitle());
            n.setDesc(overdue <= 0 ? "今天到期" : "已逾期 " + overdue + " 天");
            n.setAtText(timeText(t.getDueAt()));
            n.setAtMillis(millis(t.getDueAt()));
            n.setTodoId(t.getId());
            n.setClassId(t.getClassId());
            n.setClassName(classNames.get(t.getClassId()));
            all.add(n);
        }

        // ③ 今天生日。没有时间点，统一按"今天"显示，排在最后 —— 是提醒不是任务
        List<Student> birthday = studentMapper.selectList(
                new LambdaQueryWrapper<Student>()
                        .in(Student::getClassId, classIds)
                        .eq(Student::getStatus, 1)
                        .apply("MONTH(birthday) = MONTH(CURDATE()) AND DAY(birthday) = DAY(CURDATE())")
                        .orderByAsc(Student::getName));
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        for (Student s : birthday) {
            HomeNoticeVO n = new HomeNoticeVO();
            n.setKind("birthday");
            n.setTitle("今天是 " + s.getName() + " 的生日");
            n.setDesc("可以去群里说声生日快乐");
            n.setAtText("今天");
            n.setAtMillis(millis(todayStart));
            n.setStudentId(s.getId());
            n.setClassId(s.getClassId());
            n.setClassName(classNames.get(s.getClassId()));
            all.add(n);
        }

        /* 跨班之后必须自己排一次：三个来源各自有序，合起来是乱的。
           同一条时间戳时按班级 id 稳定一下，避免每次刷新顺序都在跳 */
        all.sort(Comparator.comparingLong(HomeNoticeVO::getAtMillis).reversed()
                .thenComparing(n -> n.getClassId() == null ? 0L : n.getClassId()));

        int cap = (limit == null || limit <= 0) ? 20 : Math.min(limit, 100);
        vo.setTotal(all.size());
        vo.setList(all.size() > cap ? new ArrayList<>(all.subList(0, cap)) : all);
        return vo;
    }

    /** 批量取学生姓名。空 id 列表直接返回，否则 selectBatchIds 会拼出 IN () 报语法错 */
    private Map<Long, String> studentNames(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, String> map = new HashMap<>();
        for (Student s : studentMapper.selectBatchIds(ids)) {
            map.put(s.getId(), s.getName());
        }
        return map;
    }

    private static String leaveTypeText(Integer type) {
        if (type == null) return "请假";
        return switch (type) {
            case 1 -> "病假";
            case 2 -> "事假";
            default -> "其他";
        };
    }

    /** "11-05 08:00 至 11-06 18:00"，跨年才带年份 */
    private static String leaveRange(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) return "";
        int year = LocalDate.now().getYear();
        boolean sameYear = start.getYear() == year && end.getYear() == year;
        String a = start.format(sameYear ? F_MDHM : F_YMD);
        String b = end.format(sameYear ? F_MDHM : F_YMD);
        return a + " 至 " + b;
    }

    /**
     * 时间文案。
     *
     * <p>未来的时间（待办还没到期）不能算"多久之前"，否则会显示成"刚刚"，
     * 所以先单独判断 isAfter，再走相对时间。
     */
    private static String timeText(LocalDateTime t) {
        if (t == null) return "";
        LocalDateTime now = LocalDateTime.now();
        if (t.isAfter(now)) {
            return t.toLocalDate().equals(now.toLocalDate())
                    ? "今天 " + t.format(F_HM)
                    : t.format(F_MDHM);
        }
        long minutes = ChronoUnit.MINUTES.between(t, now);
        if (minutes < 1) return "刚刚";
        if (minutes < 60) return minutes + " 分钟前";
        if (t.toLocalDate().equals(now.toLocalDate())) return "今天 " + t.format(F_HM);
        if (t.toLocalDate().equals(now.toLocalDate().minusDays(1))) return "昨天 " + t.format(F_HM);
        if (t.getYear() == now.getYear()) return t.format(F_MD);
        return t.format(F_YMD);
    }

    private static long millis(LocalDateTime t) {
        return t == null ? 0L : t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}