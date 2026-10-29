package com.banxing.classassistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import java.time.temporal.ChronoUnit;
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
    public HomeOverviewVO overview(Long userId) {
        // 第一步：校验身份。role 不是 1 说明是学生，不能看教师端首页
        User teacher = classService.requireTeacher(userId);

        // 第二步：查他带的班
        ClassInfo clazz = classService.myClass(userId);

        HomeOverviewVO vo = new HomeOverviewVO();
        vo.setQuote("耐心是教育的底色。");

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
        // 待办未完成：属于当前老师，且done=0
        Long todoUndone = todoMapper.selectCount(
                new LambdaQueryWrapper<Todo>()
                        .eq(Todo::getTeacherId, userId)
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
}