package com.banxing.classassistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.banxing.classassistant.common.BizException;
import com.banxing.classassistant.dto.ExamCreateDTO;
import com.banxing.classassistant.dto.ExamListVO;
import com.banxing.classassistant.dto.ExamVO;
import com.banxing.classassistant.entity.ClassInfo;
import com.banxing.classassistant.entity.Exam;
import com.banxing.classassistant.entity.Score;
import com.banxing.classassistant.mapper.ExamMapper;
import com.banxing.classassistant.mapper.ScoreMapper;
import com.banxing.classassistant.service.ClassService;
import com.banxing.classassistant.service.ExamService;
import com.banxing.classassistant.util.SubjectUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 考试业务实现
 */
@Service
@RequiredArgsConstructor
public class ExamServiceImpl implements ExamService {

    private final ClassService classService;
    private final ExamMapper examMapper;
    private final ScoreMapper scoreMapper;

    // ==================== 列表 ====================

    @Override
    public ExamListVO list(Long userId, Integer page, Integer size) {
        classService.requireTeacher(userId);

        int p = page == null || page < 1 ? 1 : page;
        int s = size == null || size < 1 ? 20 : Math.min(size, 100);

        ClassInfo clazz = classService.myClass(userId);
        if (clazz == null) {
            return new ExamListVO(0, p, s, Collections.emptyList());
        }

        LambdaQueryWrapper<Exam> qw = new LambdaQueryWrapper<>();
        qw.eq(Exam::getClassId, clazz.getId());

        // ⚠️ 顺序不能反：必须先 selectCount 再 last("LIMIT")。
        // 反了的话 count 语句会变成 COUNT(*) ... LIMIT 20，MySQL 直接语法错误
        long total = examMapper.selectCount(qw);

        qw.last("ORDER BY exam_date DESC, id DESC LIMIT " + s + " OFFSET " + (p - 1) * s);

        List<Exam> rows = examMapper.selectList(qw);
        if (rows.isEmpty()) {
            return new ExamListVO(total, p, s, Collections.emptyList());
        }

        // 一次查出这批考试各录了多少条成绩，别在循环里逐条 count（N+1）
        List<Long> examIds = rows.stream().map(Exam::getId).toList();
        Map<Long, Long> countMap = new HashMap<>();
        List<Map<String, Object>> counts = scoreMapper.selectMaps(
                new QueryWrapper<Score>()
                        .select("exam_id AS examId", "COUNT(*) AS cnt")
                        .eq("class_id", clazz.getId())
                        .in("exam_id", examIds)
                        .groupBy("exam_id"));
        for (Map<String, Object> m : counts) {
            // COUNT(*) 返回的是 Long，但 selectMaps 拿到的静态类型是 Object，
            // 按 Number 转一下，别硬转 Long（不同驱动给的实现类不一样）
            countMap.put(((Number) m.get("examId")).longValue(),
                    ((Number) m.get("cnt")).longValue());
        }

        List<ExamVO> list = new ArrayList<>(rows.size());
        for (Exam e : rows) {
            ExamVO vo = new ExamVO();
            vo.setId(e.getId());
            vo.setName(e.getName());
            vo.setType(e.getType());
            vo.setExamDate(e.getExamDate());
            vo.setSubjects(SubjectUtil.split(e.getSubjects()));
            vo.setScoreCount(countMap.getOrDefault(e.getId(), 0L));
            list.add(vo);
        }
        return new ExamListVO(total, p, s, list);
    }

    // ==================== 新建 ====================

    @Override
    public Long create(Long userId, ExamCreateDTO dto) {
        classService.requireTeacher(userId);

        ClassInfo clazz = classService.myClass(userId);
        if (clazz == null) {
            // t_exam.class_id 是 NOT NULL，没班存不进去，提前拦住
            throw new BizException(1001, "请先绑定班级");
        }

        // ---- 校验：一条一条来，报错要说清是哪一项 ----
        String name = dto.getName() == null ? "" : dto.getName().trim();
        if (name.isEmpty()) {
            throw new BizException(1001, "考试名称不能为空");
        }
        if (name.length() > 64) {
            throw new BizException(1001, "考试名称不能超过 64 字");
        }
        if (dto.getExamDate() == null) {
            throw new BizException(1001, "请选择考试日期");
        }

        List<String> subjects = normalizeSubjects(dto.getSubjects());

        // 同班同名考试不能重复建：年级排名靠「考试名 + 考试日期」跨班对齐，
        // 同班出现两个同名考试，数据会混在一起
        Long dup = examMapper.selectCount(new LambdaQueryWrapper<Exam>()
                .eq(Exam::getClassId, clazz.getId())
                .eq(Exam::getName, name));
        if (dup != null && dup > 0) {
            throw new BizException(1006, "已存在同名的考试：" + name);
        }

        Exam exam = new Exam();
        // 这三个字段只能后端决定，前端说了不算
        exam.setClassId(clazz.getId());
        exam.setName(name);
        exam.setType(dto.getType() == null ? 2 : dto.getType());
        exam.setExamDate(dto.getExamDate());
        exam.setSubjects(String.join(",", subjects));
        exam.setFullScore(SubjectUtil.writeFullScore(subjects, dto.getFullScore()));

        examMapper.insert(exam);
        return exam.getId();
    }

    // ==================== 私有方法 ====================

    /**
     * 校验科目数组：至少 1 个、每个 ≤16 字、不能重复。
     * 用 LinkedHashSet 去重并保留录入顺序（前端表头就按这个顺序画）
     */
    private List<String> normalizeSubjects(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            throw new BizException(1001, "至少要有 1 个科目");
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String r : raw) {
            String t = r == null ? "" : r.trim();
            if (t.isEmpty()) {
                throw new BizException(1001, "科目名不能为空");
            }
            if (t.length() > 16) {
                throw new BizException(1001, "科目名不能超过 16 字：" + t);
            }
            if (!seen.add(t)) {
                throw new BizException(1001, "科目重复了：" + t);
            }
        }
        return new ArrayList<>(seen);
    }
}
