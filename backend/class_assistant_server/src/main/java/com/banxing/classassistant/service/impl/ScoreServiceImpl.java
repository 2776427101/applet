package com.banxing.classassistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.banxing.classassistant.common.BizException;
import com.banxing.classassistant.dto.ScoreBatchDTO;
import com.banxing.classassistant.dto.ScoreListVO;
import com.banxing.classassistant.dto.ScoreStatsVO;
import com.banxing.classassistant.entity.ClassInfo;
import com.banxing.classassistant.entity.Exam;
import com.banxing.classassistant.entity.Score;
import com.banxing.classassistant.entity.Student;
import com.banxing.classassistant.mapper.ExamMapper;
import com.banxing.classassistant.mapper.ScoreMapper;
import com.banxing.classassistant.mapper.StudentMapper;
import com.banxing.classassistant.service.ClassService;
import com.banxing.classassistant.service.ScoreService;
import com.banxing.classassistant.util.SubjectUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 成绩业务实现
 *
 * <p>这个模块和前面几个最大的不同：<b>数据是"三维"的</b>
 * （学生 × 科目 × 考试），数据库里却是窄表的一行行。
 * 所以每个接口都要做一次"把行摊平成学生"的聚合，
 * 聚合在 Java 里做（一个班 141 行，内存里处理比写复杂 SQL 划算）。
 */
@Service
@RequiredArgsConstructor
public class ScoreServiceImpl implements ScoreService {

    private final ClassService classService;
    private final ExamMapper examMapper;
    private final ScoreMapper scoreMapper;
    private final StudentMapper studentMapper;

    // ==================== 成绩表 ====================

    @Override
    public ScoreListVO list(Long userId, Long examId) {
        ClassInfo clazz = checkAndGetClass(userId, examId);
        Exam exam = examMapper.selectById(examId);

        List<String> subjects = SubjectUtil.split(exam.getSubjects());
        Map<String, BigDecimal> fullScore = SubjectUtil.parseFullScore(exam.getFullScore(), subjects);

        // 本班全部学生（包括已离班的）。已离班的人历史成绩还在，名字不能显示成"未知"
        Map<Long, Student> stuMap = new HashMap<>();
        for (Student s : studentMapper.selectList(
                new LambdaQueryWrapper<Student>().eq(Student::getClassId, clazz.getId()))) {
            stuMap.put(s.getId(), s);
        }

        // 这次考试的全部成绩行
        List<Score> rows = scoreMapper.selectList(new LambdaQueryWrapper<Score>()
                .eq(Score::getExamId, examId)
                .eq(Score::getClassId, clazz.getId()));

        // studentId → (科目 → 分数)
        Map<Long, Map<String, BigDecimal>> byStudent = new HashMap<>();
        for (Score r : rows) {
            byStudent.computeIfAbsent(r.getStudentId(), k -> new HashMap<>())
                    .put(r.getSubject(), r.getScore());
        }

        // 要出现在表里的人 = 在读学生 ∪ 有成绩记录的学生
        // （只取在读的话，转学走的学生的成绩就消失了；只取有成绩的话，还没录的人就看不到）
        Set<Long> roster = new LinkedHashSet<>();
        for (Student s : stuMap.values()) {
            if (s.getStatus() != null && s.getStatus() == 1) {
                roster.add(s.getId());
            }
        }
        roster.addAll(byStudent.keySet());

        List<ScoreListVO.ScoreRow> list = new ArrayList<>(roster.size());
        for (Long sid : roster) {
            Student stu = stuMap.get(sid);
            if (stu == null) {
                // 成绩指向了一个已经不存在的学生（脏数据），跳过而不是报错
                continue;
            }
            ScoreListVO.ScoreRow row = new ScoreListVO.ScoreRow();
            row.setStudentId(sid);
            row.setStudentName(stu.getName());
            row.setStudentNo(stu.getStudentNo());

            Map<String, BigDecimal> sc = new LinkedHashMap<>();
            BigDecimal total = null;
            Map<String, BigDecimal> mine = byStudent.getOrDefault(sid, Map.of());
            for (String sub : subjects) {
                BigDecimal v = mine.get(sub);
                sc.put(sub, v);
                if (v != null) {
                    // 一科都没录 → total 保持 null，不是 0。
                    // 0 分和"没录"是两件事，混了之后平均分会掉下来
                    total = total == null ? v : total.add(v);
                }
            }
            row.setScores(sc);
            row.setTotal(total);
            list.add(row);
        }

        // 按总分降序，没成绩的排最后
        list.sort(Comparator.comparing(ScoreListVO.ScoreRow::getTotal,
                Comparator.nullsLast(Comparator.reverseOrder())));

        // 排名：同分并列（1、1、3），不是 1、2、3
        BigDecimal prev = null;
        int prevRank = 0;
        for (int i = 0; i < list.size(); i++) {
            ScoreListVO.ScoreRow r = list.get(i);
            if (r.getTotal() == null) {
                r.setRank(null);
                continue;
            }
            if (prev != null && prev.compareTo(r.getTotal()) == 0) {
                r.setRank(prevRank);
            } else {
                r.setRank(i + 1);
                prevRank = i + 1;
                prev = r.getTotal();
            }
        }

        ScoreListVO.ExamDetail detail = new ScoreListVO.ExamDetail();
        detail.setId(exam.getId());
        detail.setName(exam.getName());
        detail.setType(exam.getType());
        detail.setExamDate(exam.getExamDate());
        detail.setSubjects(subjects);
        detail.setFullScore(fullScore);

        ScoreListVO vo = new ScoreListVO();
        vo.setExam(detail);
        vo.setList(list);
        return vo;
    }

    // ==================== 批量录入 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchSave(Long userId, ScoreBatchDTO dto) {
        ClassInfo clazz = checkAndGetClass(userId, dto == null ? null : dto.getExamId());
        Exam exam = examMapper.selectById(dto.getExamId());

        List<ScoreBatchDTO.Row> rows = dto.getRows();
        if (rows == null || rows.isEmpty()) {
            throw new BizException(1001, "至少要有一行成绩");
        }
        if (rows.size() > 200) {
            // 一个班最多几十人，200 是防御性上限：防止有人传 10 万行把数据库打死
            throw new BizException(1001, "一次最多提交 200 个学生");
        }

        List<String> subjects = SubjectUtil.split(exam.getSubjects());
        Set<String> subjectSet = new HashSet<>(subjects);
        Map<String, BigDecimal> fullScore = SubjectUtil.parseFullScore(exam.getFullScore(), subjects);

        // ---- 一次性核对所有 studentId 的班级归属 ----
        // 千万别在下面的循环里逐个 selectById，那就是 N+1 查询
        Set<Long> stuIds = new LinkedHashSet<>();
        for (ScoreBatchDTO.Row r : rows) {
            if (r.getStudentId() == null) {
                throw new BizException(1001, "有一行缺少 studentId");
            }
            stuIds.add(r.getStudentId());
        }
        Set<Long> inClass = new HashSet<>();
        for (Student s : studentMapper.selectList(new LambdaQueryWrapper<Student>()
                .eq(Student::getClassId, clazz.getId())
                .in(Student::getId, stuIds))) {
            inClass.add(s.getId());
        }
        for (Long id : stuIds) {
            if (!inClass.contains(id)) {
                throw new BizException(3002, "学生 " + id + " 不在你的班里");
            }
        }

        // ---- 校验并摊平成一行行成绩 ----
        List<Score> flat = new ArrayList<>();
        for (ScoreBatchDTO.Row r : rows) {
            if (r.getScores() == null || r.getScores().isEmpty()) {
                continue;
            }
            for (Map.Entry<String, BigDecimal> e : r.getScores().entrySet()) {
                String sub = e.getKey();
                if (!subjectSet.contains(sub)) {
                    throw new BizException(1001, "科目「" + sub + "」不在本次考试里");
                }
                BigDecimal v = e.getValue();
                BigDecimal fs = fullScore.getOrDefault(sub, new BigDecimal("100"));
                if (v != null) {
                    if (v.compareTo(BigDecimal.ZERO) < 0) {
                        throw new BizException(1001, "「" + sub + "」分数不能为负");
                    }
                    if (v.compareTo(fs) > 0) {
                        throw new BizException(1001,
                                "「" + sub + "」超过满分 " + fs.stripTrailingZeros().toPlainString());
                    }
                }
                Score s = new Score();
                s.setClassId(clazz.getId());
                s.setExamId(exam.getId());
                s.setStudentId(r.getStudentId());
                s.setSubject(sub);
                s.setScore(v);        // null = 缺考，会照样写进去
                s.setFullScore(fs);
                flat.add(s);
            }
        }
        if (flat.isEmpty()) {
            throw new BizException(1001, "没有可保存的成绩");
        }

        // 一条 SQL 全部写完。方法上有 @Transactional，
        // 中途任何异常都会整批回滚，不会出现"前 20 个存了，第 21 个失败"
        scoreMapper.batchUpsert(flat);
        return flat.size();
    }

    // ==================== 统计 ====================

    @Override
    public ScoreStatsVO stats(Long userId, Long examId) {
        ClassInfo clazz = checkAndGetClass(userId, examId);
        Exam exam = examMapper.selectById(examId);

        List<String> subjects = SubjectUtil.split(exam.getSubjects());
        Map<String, BigDecimal> fullScore = SubjectUtil.parseFullScore(exam.getFullScore(), subjects);

        List<Score> rows = scoreMapper.selectList(new LambdaQueryWrapper<Score>()
                .eq(Score::getExamId, examId)
                .eq(Score::getClassId, clazz.getId()));

        // ---- 单科统计 ----
        Map<String, List<BigDecimal>> bySubject = new LinkedHashMap<>();
        for (String s : subjects) {
            bySubject.put(s, new ArrayList<>());
        }
        // 每个学生的总分，用来算总分统计
        Map<Long, BigDecimal> stuTotal = new HashMap<>();

        for (Score r : rows) {
            if (r.getScore() != null) {
                List<BigDecimal> bucket = bySubject.get(r.getSubject());
                if (bucket != null) {
                    bucket.add(r.getScore());
                }
                stuTotal.merge(r.getStudentId(), r.getScore(), BigDecimal::add);
            }
        }

        List<ScoreStatsVO.SubjectStat> stats = new ArrayList<>();
        for (String sub : subjects) {
            List<BigDecimal> bucket = bySubject.get(sub);
            BigDecimal fs = fullScore.getOrDefault(sub, new BigDecimal("100"));

            ScoreStatsVO.SubjectStat st = new ScoreStatsVO.SubjectStat();
            st.setSubject(sub);
            st.setFullScore(fs);

            if (bucket.isEmpty()) {
                // 这一科还没人录：全部给 null，前端显示「—」。
                // 给 0 会被误读成"全班考了 0 分"
                stats.add(st);
                continue;
            }

            BigDecimal sum = BigDecimal.ZERO;
            BigDecimal max = bucket.get(0);
            BigDecimal min = bucket.get(0);
            int pass = 0;
            // 及格线 = 满分的 60%
            BigDecimal passLine = fs.multiply(new BigDecimal("0.6"));
            for (BigDecimal v : bucket) {
                sum = sum.add(v);
                if (v.compareTo(max) > 0) {
                    max = v;
                }
                if (v.compareTo(min) < 0) {
                    min = v;
                }
                if (v.compareTo(passLine) >= 0) {
                    pass++;
                }
            }
            st.setAvg(sum.divide(new BigDecimal(bucket.size()), 1, RoundingMode.HALF_UP));
            st.setMax(max);
            st.setMin(min);
            st.setPassRate(new BigDecimal(pass)
                    .divide(new BigDecimal(bucket.size()), 2, RoundingMode.HALF_UP));
            stats.add(st);
        }

        ScoreStatsVO vo = new ScoreStatsVO();
        vo.setExamId(examId);
        vo.setSubjects(stats);

        // ---- 总分统计 ----
        if (!stuTotal.isEmpty()) {
            BigDecimal sum = BigDecimal.ZERO;
            BigDecimal max = null;
            BigDecimal min = null;
            for (BigDecimal v : stuTotal.values()) {
                sum = sum.add(v);
                if (max == null || v.compareTo(max) > 0) {
                    max = v;
                }
                if (min == null || v.compareTo(min) < 0) {
                    min = v;
                }
            }
            vo.setTotalAvg(sum.divide(new BigDecimal(stuTotal.size()), 1, RoundingMode.HALF_UP));
            vo.setTotalMax(max);
            vo.setTotalMin(min);
        }

        // ---- 年级排名 ----
        // 按「考试名 + 考试日期」跨班对齐（t_exam 是按班存的，examId 跨不了班）
        List<Map<String, Object>> classAvg = scoreMapper.selectClassAvg(
                exam.getName(), exam.getExamDate(), clazz.getGrade());

        vo.setGradeClassCount(classAvg.size());

        // 同年级只有本班一个班参与 → 排第 1 是假象，必须返回 null。
        // 前端显示「未统计」，比撒谎强
        if (classAvg.size() < 2) {
            vo.setGradeRank(null);
            return vo;
        }
        int rank = 0;
        for (int i = 0; i < classAvg.size(); i++) {
            Number cid = (Number) classAvg.get(i).get("classId");
            if (cid != null && cid.longValue() == clazz.getId()) {
                rank = i + 1;   // 查询已经按 avgTotal 降序，位置就是名次
                break;
            }
        }
        vo.setGradeRank(rank == 0 ? null : rank);

        return vo;
    }

    // ==================== 私有方法 ====================

    /**
     * 三个接口共用的前置校验：是不是老师、有没有班、这次考试是不是你班的。
     * 校验不过直接抛异常，通过就返回本班信息。
     */
    private ClassInfo checkAndGetClass(Long userId, Long examId) {
        classService.requireTeacher(userId);

        if (examId == null) {
            throw new BizException(1001, "缺少 examId");
        }
        ClassInfo clazz = classService.myClass(userId);
        if (clazz == null) {
            throw new BizException(1004, "考试不存在");
        }
        Exam exam = examMapper.selectById(examId);
        if (exam == null) {
            throw new BizException(1004, "考试不存在");
        }
        // 1004 和 3002 不能混：混用会让前端以为"这次考试被删了"从而把它从列表里移除
        if (!exam.getClassId().equals(clazz.getId())) {
            throw new BizException(3002, "这次考试不属于你的班");
        }
        return clazz;
    }
}
