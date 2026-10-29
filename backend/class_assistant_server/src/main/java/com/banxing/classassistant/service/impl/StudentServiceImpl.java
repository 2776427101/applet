package com.banxing.classassistant.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.banxing.classassistant.common.BizException;
import com.banxing.classassistant.dto.ScoreListVO;
import com.banxing.classassistant.dto.StudentDetailVO;
import com.banxing.classassistant.dto.StudentListVO;
import com.banxing.classassistant.dto.StudentSaveDTO;
import com.banxing.classassistant.dto.StudentVO;
import com.banxing.classassistant.entity.ClassInfo;
import com.banxing.classassistant.entity.Exam;
import com.banxing.classassistant.entity.LeaveRequest;
import com.banxing.classassistant.entity.Score;
import com.banxing.classassistant.entity.Seat;
import com.banxing.classassistant.entity.Student;
import com.banxing.classassistant.mapper.ExamMapper;
import com.banxing.classassistant.mapper.LeaveRequestMapper;
import com.banxing.classassistant.mapper.ScoreMapper;
import com.banxing.classassistant.mapper.SeatMapper;
import com.banxing.classassistant.mapper.StudentMapper;
import com.banxing.classassistant.service.ClassService;
import com.banxing.classassistant.service.ScoreService;
import com.banxing.classassistant.service.StudentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
/**
 * 学生业务实现
 * 全是读操作，不涉及事务，所以不加 @Transactional（事物管理注解）
 */
@Service
// Lombok自动生成构造函数
@RequiredArgsConstructor
public class StudentServiceImpl implements StudentService {
    private final ClassService classService;
    private final StudentMapper studentMapper;

    /* 详情页要用的三个查询 + 一个成绩服务（排名复用它的实现，不在这里抄第二遍） */
    private final SeatMapper seatMapper;
    private final LeaveRequestMapper leaveMapper;
    private final ExamMapper examMapper;
    private final ScoreMapper scoreMapper;
    private final ScoreService scoreService;
    @Override
    public StudentListVO list(Long userId, String keyword, Integer status) {
        // 第一步：校验身份。role 不是 1 说明是学生，不能看教师端
        // 拦截器只验了 token 有没有效，没验角色，所以每个教师端接口都要自己查一次
        classService.requireTeacher(userId);
        // 第二步：查他带的班。
        // 注意这里用的是 userId，不是前端传的 classId —— 前端传什么都不能信
        ClassInfo clazz = classService.myClass(userId);
        // 第三步：没绑班就返回空列表，不报错。
        // 前端拿到 total=0、list=[] 会显示「这个班还没有学生」，这是正常状态
        if (clazz == null) {
            return new StudentListVO(0, Collections.emptyList());
        }
        // 第四步：拼查询条件
        LambdaQueryWrapper<Student> qw = new LambdaQueryWrapper<>();
        // 4.1 班级条件：漏了这个就能看到别的班的人
        qw.eq(Student::getClassId, clazz.getId());
        // 4.2 状态条件：三种情况分开处理
        if (status == null) {
            // 不传默认只看在读，和首页的 studentCount 口径一致
            qw.eq(Student::getStatus, 1);
        } else if (status != -1) {
            // -1 表示全部，不加条件；0 和 1 才加
            qw.eq(Student::getStatus, status);
        }
        // 4.3 关键词：姓名或学号，两个字段任意命中一个就算
        if (StringUtils.hasText(keyword)) {
            String k = keyword.trim();
            // and(...) 会生成 (name LIKE ? OR student_no LIKE ?)，
            // 括号很重要：不加括号会和上面的 class_id 条件混在一起，
            // 变成 "class_id=? AND name LIKE ? OR student_no LIKE ?"，
            // 后面的 OR 会让别的班的学生漏出来
            qw.and(w -> w.like(Student::getName, k)
                    .or()
                    .like(Student::getStudentNo, k));
        }
        // 4.4 排序：学号为 NULL 的排最后。
        // MySQL 默认把 NULL 当最小值排最前，名单一开头会全是没填学号的人。
        // "student_no IS NULL" 在 MySQL 里结果是 0 或 1，先按它排（0 在前），再按学号排
        qw.last("ORDER BY student_no IS NULL, student_no");
        // 第五步：查
        List<Student> rows = studentMapper.selectList(qw);
        // 第六步：实体转 VO。
        // stream().map(StudentVO::from) 是方法引用，等价于 s -> StudentVO.from(s)
        List<StudentVO> list = rows.stream().map(StudentVO::from).toList();
        return new StudentListVO(list.size(), list);
    }

    @Override
    public StudentDetailVO detail(Long userId, Long studentId) {
        // 身份 + 班级 + 学生归属，三件事一起校验（detail / update / remove 共用）
        Student s = requireMyStudent(userId, studentId);

        StudentDetailVO vo = new StudentDetailVO();
        vo.setId(s.getId());
        vo.setName(s.getName());
        vo.setGender(s.getGender());
        vo.setBirthday(s.getBirthday());
        vo.setStudentNo(s.getStudentNo());
        vo.setStatus(s.getStatus());
        vo.setCreatedAt(s.getCreatedAt());

        // 9 个扩展字段原样返回。null 就是「没填」，前端显示「未填写」——
        // 这里不要把它们变成空字符串，否则前端没法用 v-if 判断
        vo.setDormitory(s.getDormitory());
        vo.setPhone(s.getPhone());
        vo.setWechat(s.getWechat());
        vo.setFamilyPhone1(s.getFamilyPhone1());
        vo.setFamilyPhone2(s.getFamilyPhone2());
        vo.setTrainingDirection(s.getTrainingDirection());
        vo.setNation(s.getNation());
        vo.setPoliticalStatus(s.getPoliticalStatus());
        vo.setPartyActivist(s.getPartyActivist());

        // 坐哪。没排座就是 null，不是 (0,0) ——
        // 前端靠 null 显示「未排座」，靠 (0,0) 会显示「第 0 排第 0 列」
        Seat seat = seatMapper.selectOne(new LambdaQueryWrapper<Seat>()
                .eq(Seat::getClassId, s.getClassId())
                .eq(Seat::getStudentId, studentId)
                .last("LIMIT 1"));
        if (seat != null) {
            StudentDetailVO.SeatPos pos = new StudentDetailVO.SeatPos();
            pos.setRow(seat.getRowNo());
            pos.setCol(seat.getColNo());
            vo.setSeat(pos);
        }

        // 第四步：请假统计。
        // 一个人一学期最多十来条，全查回来在内存里算，比写三条 COUNT SQL 清楚
        List<LeaveRequest> leaves = leaveMapper.selectList(new LambdaQueryWrapper<LeaveRequest>()
                .eq(LeaveRequest::getStudentId, studentId)
                .orderByDesc(LeaveRequest::getStartAt));
        if (!leaves.isEmpty()) {
            StudentDetailVO.LeaveStat stat = new StudentDetailVO.LeaveStat();
            int passed = 0;
            int pending = 0;
            for (LeaveRequest l : leaves) {
                Integer st = l.getStatus();
                if (st == null) {
                    continue;
                }
                if (st == 0) {
                    pending++;
                }
                // 1=已通过 3=已销假 才算缺课；2=已驳回没缺课，不能算进去
                if (st == 1 || st == 3) {
                    passed++;
                }
            }
            stat.setTotal(passed);
            stat.setPending(pending);

            // 「最近一次」也要跳过被驳回的，
            // 否则卡片会显示「上次请假 10-20（那条被驳回了）」，容易误读
            LeaveRequest last = leaves.stream()
                    .filter(l -> l.getStatus() == null || l.getStatus() != 2)
                    .findFirst()
                    .orElse(null);
            if (last != null) {
                stat.setLastDate(last.getStartAt() == null ? null : last.getStartAt().toLocalDate());
                stat.setLastType(last.getType());
            }
            vo.setLeave(stat);
        }

        // 最近一次考试的成绩。
        // 排名不在这里算 —— ScoreService.list 已经算好了，
        // 抄一遍就是两份实现，改一处漏一处
        Exam exam = examMapper.selectOne(new LambdaQueryWrapper<Exam>()
                .eq(Exam::getClassId, s.getClassId())
                .orderByDesc(Exam::getExamDate)
                .orderByDesc(Exam::getId)
                .last("LIMIT 1"));

        // 光有考试不够，还得确认<b>这个人</b>在那次考试里有成绩记录。
        //
        // 不加这一步的话，刚转来的新生会被显示成「语文缺考 / 数学缺考 / 英语缺考」——
        // 但他不是缺考，是那次考试时还不在班里。这两件事对老师的意义完全不同：
        // 缺考要去问原因，没参加那次考试什么都不用管。
        if (exam != null && hasAnyScore(exam.getId(), studentId)) {
            ScoreListVO scoreList = scoreService.list(userId, exam.getId());
            ScoreListVO.ScoreRow mine = scoreList.getList().stream()
                    .filter(r -> studentId.equals(r.getStudentId()))
                    .findFirst()
                    .orElse(null);
            if (mine != null) {
                StudentDetailVO.LatestScore ls = new StudentDetailVO.LatestScore();
                ls.setExamId(exam.getId());
                ls.setExamName(exam.getName());
                ls.setExamDate(exam.getExamDate());
                ls.setTotal(mine.getTotal());
                ls.setRank(mine.getRank());
                // 班级人数取成绩表行数，不是学生表人数：
                // 转学走的人不该出现在分母里
                ls.setClassSize(scoreList.getList().size());

                // 按考试设定的科目顺序装成 List，缺考也占一行（score = null）
                List<String> subjects = scoreList.getExam() == null || scoreList.getExam().getSubjects() == null
                        ? List.of()
                        : scoreList.getExam().getSubjects();
                Map<String, BigDecimal> fullMap = scoreList.getExam() == null
                        || scoreList.getExam().getFullScore() == null
                        ? Map.of()
                        : scoreList.getExam().getFullScore();

                List<StudentDetailVO.SubjectScore> subs = new ArrayList<>();
                for (String subject : subjects) {
                    StudentDetailVO.SubjectScore ss = new StudentDetailVO.SubjectScore();
                    ss.setSubject(subject);
                    ss.setScore(mine.getScores() == null ? null : mine.getScores().get(subject));
                    ss.setFullScore(fullMap.get(subject));
                    subs.add(ss);
                }
                ls.setSubjects(subs);
                vo.setLatestScore(ls);
            }
        }

        return vo;
    }

    @Override
    public Long create(Long userId, StudentSaveDTO dto) {
        // 新增没有「学生归属」可校验，只能校验身份 + 班级
        classService.requireTeacher(userId);
        ClassInfo clazz = classService.myClass(userId);
        if (clazz == null) {
            // 和 list / detail 不一样：那两个「没绑班」返回空数据是合理的，
            // 但新增必须有地方落库，没班就是真的没法做
            throw new BizException(1001, "你还没有绑定班级，不能添加学生");
        }

        // 学号可以为空。空字符串要当成 null 存，
        // 否则「没填学号」会变成「学号是空字符串」，两个空学号之间还会互相冲突
        String no = trimToNull(dto.getStudentNo());
        if (no != null) {
            checkStudentNoUnique(clazz.getId(), no, null);
        }

        Student s = new Student();
        s.setClassId(clazz.getId());
        s.setName(dto.getName().trim());
        // 不传性别按 0（未知），契约 §6.3
        s.setGender(dto.getGender() == null ? 0 : dto.getGender());
        s.setBirthday(dto.getBirthday());
        s.setStudentNo(no);
        // 新加的人一定是在读，这个字段不给前端传
        s.setStatus(1);

        // 9 个扩展字段：全部走 trimToNull。
        // 「手机号填了一堆空格」要存成 NULL 而不是 "   "，
        // 否则详情页会显示一个看不见内容、但 v-if 判定为「已填写」的格子
        s.setDormitory(trimToNull(dto.getDormitory()));
        s.setPhone(trimToNull(dto.getPhone()));
        s.setWechat(trimToNull(dto.getWechat()));
        s.setFamilyPhone1(trimToNull(dto.getFamilyPhone1()));
        s.setFamilyPhone2(trimToNull(dto.getFamilyPhone2()));
        s.setTrainingDirection(trimToNull(dto.getTrainingDirection()));
        s.setNation(trimToNull(dto.getNation()));
        s.setPoliticalStatus(trimToNull(dto.getPoliticalStatus()));
        s.setPartyActivist(trimToNull(dto.getPartyActivist()));

        studentMapper.insert(s);
        return s.getId();
    }

    @Override
    public void update(Long userId, Long studentId, StudentSaveDTO dto) {
        Student old = requireMyStudent(userId, studentId);

        String no = trimToNull(dto.getStudentNo());
        if (no != null) {
            // 排除自己：把学号原样传回来不该报「已被自己占用」
            checkStudentNoUnique(old.getClassId(), no, studentId);
        }

        // 用 LambdaUpdateWrapper 显式 set，不用 updateById。
        //
        // 为什么：updateById 走的是实体的非空字段策略，null 字段会被跳过。
        // 老师把生日清空（传 null）时，生日会保持旧值 ——
        // 而契约 §6.4 说 PUT 是全量提交，「没传的字段要清空」。
        // 这两个语义是冲突的，必须用 set 才能表达「就是要把生日改成 null」。
        studentMapper.update(null, new LambdaUpdateWrapper<Student>()
                .eq(Student::getId, studentId)
                // 再带一次 class_id：上面虽然已经校验过，
                // 但更新语句里带上是零成本的兜底，防将来有人改动漏掉校验
                .eq(Student::getClassId, old.getClassId())
                .set(Student::getName, dto.getName().trim())
                .set(Student::getGender, dto.getGender() == null ? 0 : dto.getGender())
                .set(Student::getBirthday, dto.getBirthday())
                .set(Student::getStudentNo, no)
                // 9 个扩展字段也全部无条件 set。
                // 这是「全量提交」最容易出事的地方：编辑页如果只提交了姓名和学号，
                // 这里会把手机号、宿舍、家人电话统统清成 NULL。
                // 所以前端 edit.vue 必须把 detail 拿到的整份数据原样回传（见 §6.4）
                .set(Student::getDormitory, trimToNull(dto.getDormitory()))
                .set(Student::getPhone, trimToNull(dto.getPhone()))
                .set(Student::getWechat, trimToNull(dto.getWechat()))
                .set(Student::getFamilyPhone1, trimToNull(dto.getFamilyPhone1()))
                .set(Student::getFamilyPhone2, trimToNull(dto.getFamilyPhone2()))
                .set(Student::getTrainingDirection, trimToNull(dto.getTrainingDirection()))
                .set(Student::getNation, trimToNull(dto.getNation()))
                .set(Student::getPoliticalStatus, trimToNull(dto.getPoliticalStatus()))
                .set(Student::getPartyActivist, trimToNull(dto.getPartyActivist())));
    }

    @Override
    @Transactional
    public void remove(Long userId, Long studentId) {
        Student s = requireMyStudent(userId, studentId);

        // 已经离班的直接返回，不报错。
        // 这个接口是幂等的：老师网络卡了一下点两次，第二次不该弹「操作失败」
        if (s.getStatus() != null && s.getStatus() == 0) {
            return;
        }

        // 软删除：只改 status，不 DELETE。
        // 请假、成绩都还指着这个 student_id，真删了历史数据就成孤儿了
        studentMapper.update(null, new LambdaUpdateWrapper<Student>()
                .eq(Student::getId, studentId)
                .eq(Student::getClassId, s.getClassId())
                .set(Student::getStatus, 0));

        // 顺手把这个人的座位清掉。
        // 座位读接口已经把离班学生的格子当空位返回，所以不清也「看起来」是对的，
        // 但数据库里还留着他占着 3 排 5 列 —— 下次有人查库会以为数据错了。
        // 两个写操作，所以这个方法加了 @Transactional
        seatMapper.update(null, new LambdaUpdateWrapper<Seat>()
                .eq(Seat::getClassId, s.getClassId())
                .eq(Seat::getStudentId, studentId)
                .set(Seat::getStudentId, null));
    }

    /* ================= 下面两个是私有工具，只在本类用 ================= */

    /**
     * 校验当前用户是老师、且这个学生在他带的班里，返回学生实体。
     *
     * <p>detail / update / remove 三个方法开头都要做这件事，抄三遍迟早漏一处。
     *
     * <p>1004（不存在）和 3002（不是你的班）必须分开：
     * 合成一个「查不到」，前端就没法区分「这孩子被删了」和「你点错班了」，
     * 改 URL 试别人 id 时也看不出到底是哪种。
     */
    private Student requireMyStudent(Long userId, Long studentId) {
        classService.requireTeacher(userId);
        ClassInfo clazz = classService.myClass(userId);

        Student s = studentMapper.selectById(studentId);
        if (s == null) {
            throw new BizException(1004, "学生不存在");
        }
        if (clazz == null || !clazz.getId().equals(s.getClassId())) {
            throw new BizException(3002, "这个学生不在你的班");
        }
        return s;
    }

    /**
     * 这个人在这次考试里有没有任何一条成绩记录。
     *
     * <p>注意区分两种「没有分数」：
     * <ul>
     *   <li>有记录、但 score 是 null → <b>缺考</b>，返回 true</li>
     *   <li>一行记录都没有 → <b>那次考试时他不在班里</b>（转学生），返回 false</li>
     * </ul>
     * 详情页要分开显示，所以这里只问「有没有行」，不看分数值。
     */
    private boolean hasAnyScore(Long examId, Long studentId) {
        return scoreMapper.selectCount(new LambdaQueryWrapper<Score>()
                .eq(Score::getExamId, examId)
                .eq(Score::getStudentId, studentId)) > 0;
    }

    /**
     * 同班内学号不能重复。
     *
     * <p>用 1006 而不是 1001：1001 是「你填的格式不对」，用户自己能改；
     * 1006 是「和已有数据冲突」，需要用户去核对是谁占了。
     * 错误信息里带上占用者的姓名，老师不用再翻名单。
     *
     * <p><b>已离班的学生也算占用。</b>学号是终身的，离班学生的成绩、请假记录
     * 都还挂着这个学号，给新生复用会让人对不上历史数据。
     *
     * @param excludeId 编辑时传自己的 id，避免「学号没改」被判成重复
     */
    private void checkStudentNoUnique(Long classId, String studentNo, Long excludeId) {
        LambdaQueryWrapper<Student> qw = new LambdaQueryWrapper<Student>()
                .eq(Student::getClassId, classId)
                .eq(Student::getStudentNo, studentNo);
        if (excludeId != null) {
            qw.ne(Student::getId, excludeId);
        }
        Student exist = studentMapper.selectOne(qw.last("LIMIT 1"));
        if (exist != null) {
            throw new BizException(1006, "学号 " + studentNo + " 已被 " + exist.getName() + " 使用");
        }
    }

    /**
     * 去首尾空格，空字符串转成 null。
     *
     * <p>为什么不直接用 {@code StringUtils.hasText}：
     * 数据库里 " " 和 "" 和 NULL 是三种不同的值，但业务上都是「没填学号」。
     * 统一成 NULL 才能让唯一性校验正常工作 ——
     * 否则两个没填学号的学生会因为学号都是 "" 而互相冲突。
     */
    private String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
