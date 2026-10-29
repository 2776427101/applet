package com.banxing.classassistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.banxing.classassistant.common.BizException;
import com.banxing.classassistant.dto.ScoreImportCommitDTO;
import com.banxing.classassistant.dto.ScoreImportPreviewVO;
import com.banxing.classassistant.dto.ScoreImportResultVO;
import com.banxing.classassistant.entity.ClassInfo;
import com.banxing.classassistant.entity.Exam;
import com.banxing.classassistant.entity.Score;
import com.banxing.classassistant.entity.Student;
import com.banxing.classassistant.importer.HeaderMapping;
import com.banxing.classassistant.importer.ImportField;
import com.banxing.classassistant.importer.ParserRegistry;
import com.banxing.classassistant.importer.SheetData;
import com.banxing.classassistant.importer.WorkbookParser;
import com.banxing.classassistant.mapper.ExamMapper;
import com.banxing.classassistant.mapper.ScoreMapper;
import com.banxing.classassistant.mapper.StudentMapper;
import com.banxing.classassistant.service.ClassService;
import com.banxing.classassistant.service.ScoreImportService;
import com.banxing.classassistant.util.SubjectUtil;
import com.banxing.classassistant.util.TextNormalize;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 成绩表导入实现。
 *
 * <p><b>和学生名单导入最大的不同：这里的「一列」是数据，不是元数据。</b>
 * 名单的列是固定的（姓名、学号、班级…），成绩表的列名就是科目名，
 * 而科目是每场考试自己配的。所以「表头识别」这一层要分成两段：
 * <ol>
 *   <li>先用现成的 {@link HeaderMapping} 认出姓名 / 学号 / 班级 —— 这部分和名单完全一样</li>
 *   <li>剩下的列再拿去做「列名 → 科目」的匹配</li>
 * </ol>
 *
 * <p><b>整个类里最容易出人命的一条规则</b>：
 * 表里没有的科目，{@code scores} 里绝不能出现这个 key。
 * 因为「key 存在但值是 null」表示「这个人这科缺考」，
 * 后端会把它当成一次明确的操作去清空。表里只有语数两科，
 * 却给英语补一个 null，全班英语分数就没了。
 */
@Service
@RequiredArgsConstructor
public class ScoreImportServiceImpl implements ScoreImportService {

    /** 上传上限。一个班的成绩表撑死几十 KB */
    private static final long MAX_FILE_BYTES = 5L * 1024 * 1024;

    /** 各科满分的兜底值，和 ScoreServiceImpl 保持一致 */
    private static final BigDecimal DEFAULT_FULL = new BigDecimal("100");

    /**
     * 这些写法都表示「这一科没考」。
     *
     * <p>为什么把 {@code -} 和 {@code /} 也算进来：老师手工填的表格里，
     * 缺考经常就是一个横杠或者斜杠。Excel 里「—」和「–」长得几乎一样，
     * 但编码不同，所以几个都列上。
     */
    private static final Set<String> ABSENT = Set.of(
            "缺考", "缺", "未考", "没考", "无", "空", "免考", "免修",
            "-", "--", "—", "——", "–", "/", "／", "×", "x", "\\");

    /**
     * 科目列名的尾巴，去掉之后再和考试科目比。
     *
     * <p>顺序有讲究：长的在前。「语文成绩」要先去「成绩」，
     * 如果先试「分」，什么都不会发生（不以「分」结尾），倒也不出错；
     * 但「语文分数」如果先试「分」会变成「语文数」，就匹配不上了。
     */
    private static final List<String> SUBJECT_SUFFIX = List.of("成绩", "分数", "得分", "分值", "分");

    private final ClassService classService;
    private final ExamMapper examMapper;
    private final ScoreMapper scoreMapper;
    private final StudentMapper studentMapper;
    private final ParserRegistry parserRegistry;

    /* ================= 预览 ================= */

    @Override
    public ScoreImportPreviewVO preview(Long userId, Long examId, MultipartFile file) {
        ClassInfo clazz = requireClass(userId);
        Exam exam = requireExam(clazz, examId);

        if (file == null || file.isEmpty()) {
            throw new BizException(1001, "请选择要导入的文件");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new BizException(1001, "文件超过 5MB，成绩表一般只有几十 KB，请确认没传错文件");
        }

        List<String> subjects = SubjectUtil.split(exam.getSubjects());
        if (subjects.isEmpty()) {
            throw new BizException(1001, "这次考试还没设置科目，先去考试页补上再导入成绩");
        }
        Map<String, BigDecimal> fullScore = SubjectUtil.parseFullScore(exam.getFullScore(), subjects);

        String filename = baseName(file.getOriginalFilename());
        WorkbookParser parser = parserRegistry.require(filename);

        SheetData sheet;
        try (InputStream in = file.getInputStream()) {
            sheet = parser.parse(in);
        } catch (IOException e) {
            throw new BizException(1001, e.getMessage());
        }

        HeaderMapping map = HeaderMapping.detect(sheet);
        if (map == null) {
            throw new BizException(1001,
                    "在前 " + HeaderMapping.SCAN_ROWS + " 行里没找到「姓名」列。"
                            + "成绩表至少要有「姓名」一列，其他列写科目名");
        }

        ColumnPlan plan = mapColumns(map, subjects);

        // 一列科目都没认出来 —— 后面每行都会「没有任何分数」，报错会淹没真正的原因
        if (plan.subjectCol.isEmpty()) {
            throw new BizException(1001,
                    "表里没找到能对上本次考试科目的列。本次考试的科目是"
                            + String.join("、", subjects) + "，请把列名写成科目名（如「语文」「数学成绩」）");
        }

        Map<String, Student> byNo = studentsByNo(clazz.getId());
        Map<String, List<Student>> byName = studentsByName(clazz.getId());
        Map<Long, Map<String, BigDecimal>> existing = existingScores(clazz.getId(), examId);

        ScoreImportPreviewVO vo = new ScoreImportPreviewVO();
        vo.setExamId(examId);
        vo.setExamName(exam.getName());
        vo.setSheetName(sheet.getSheetName());
        vo.setHeaderRowNo(map.getExcelRowNo());
        vo.setBlankRows(sheet.getBlankRows());
        vo.setSubjects(subjects);
        vo.setFullScore(fullScore);
        vo.setColumns(plan.columns);
        vo.setUnmappedHeaders(plan.unmapped);
        vo.setMissingSubjects(plan.missing);

        List<ScoreImportPreviewVO.RowVO> rows = new ArrayList<>();
        ScoreImportPreviewVO.StatsVO stats = new ScoreImportPreviewVO.StatsVO();

        // 同一个学生在表里出现两行 → 谁的分算数说不清，直接判错
        Map<Long, Integer> seenStudent = new HashMap<>();

        for (int i = map.getRowIndex() + 1; i < sheet.size(); i++) {
            if (sheet.isBlankRow(i)) {
                continue;
            }

            ScoreImportPreviewVO.RowVO row = new ScoreImportPreviewVO.RowVO();
            row.setExcelRowNo(sheet.excelRowNo(i));
            row.setName(TextNormalize.text(cellOf(sheet, i, map, ImportField.NAME)));
            row.setStudentNo(TextNormalize.studentNo(cellOf(sheet, i, map, ImportField.STUDENT_NO)));

            List<String> errors = new ArrayList<>();

            // 表里带了「班级」列就校验一下。成绩表通常没有这一列，
            // 但一旦有，就说明这张表可能是好几个班合在一起的
            String rawClass = TextNormalize.text(cellOf(sheet, i, map, ImportField.CLASS_NAME));
            if (rawClass != null && !TextNormalize.sameClass(rawClass, clazz.getName())) {
                errors.add("班级是「" + rawClass + "」，不是本班「" + clazz.getName() + "」");
            }

            Student stu = matchStudent(row, byNo, byName, errors);

            Map<String, BigDecimal> scores = new LinkedHashMap<>();
            for (Map.Entry<String, Integer> e : plan.subjectCol.entrySet()) {
                String subject = e.getKey();
                CellResult cell = parseCell(sheet.get(i, e.getValue()), fullScore.get(subject));
                if (cell.error != null) {
                    errors.add(subject + "：" + cell.error);
                } else {
                    // 空值也放进去（= 缺考）。表里有这一列，就代表「这一科这次要导」，
                    // 至于要不要真写 null，下面统计时按「库里有没有旧分」再判一次
                    scores.put(subject, cell.value);
                }
            }
            row.setScores(scores);

            if (stu != null) {
                row.setStudentId(stu.getId());
                // 表里没写名字（只有学号）时，用库里的名字补上，预览才看得懂
                if (row.getName() == null) {
                    row.setName(stu.getName());
                }
                Integer first = seenStudent.putIfAbsent(stu.getId(), row.getExcelRowNo());
                if (first != null) {
                    errors.add("和第 " + first + " 行是同一个学生（" + stu.getName() + "），只能留一行");
                }
            }

            if (!errors.isEmpty()) {
                row.setAction("error");
                row.setErrors(errors);
                row.setReason(errors.get(0));
                stats.setError(stats.getError() + 1);
                rows.add(row);
                continue;
            }

            // ---- 按格统计 ----
            Map<String, BigDecimal> old = existing.getOrDefault(stu.getId(), Map.of());
            boolean touchesOld = false;
            for (Map.Entry<String, BigDecimal> e : scores.entrySet()) {
                BigDecimal oldV = old.get(e.getKey());
                if (e.getValue() == null) {
                    // 表里空、库里也空 → 什么都不用写。
                    // 写成 null 只是把 NULL 再写一遍，白白多一条 SQL，也让「会写入 N 格」虚高
                    if (oldV != null) {
                        stats.setClear(stats.getClear() + 1);
                        stats.setCells(stats.getCells() + 1);
                        touchesOld = true;
                    }
                } else if (oldV != null) {
                    stats.setOverwrite(stats.getOverwrite() + 1);
                    stats.setCells(stats.getCells() + 1);
                    touchesOld = true;
                } else {
                    stats.setCreate(stats.getCreate() + 1);
                    stats.setCells(stats.getCells() + 1);
                }
            }
            row.setAction(touchesOld ? "overwrite" : "create");
            rows.add(row);
        }

        stats.setRows(rows.size());
        stats.setImportable(rows.size() - stats.getError());
        vo.setDataRows(rows.size());
        vo.setStats(stats);
        vo.setRows(rows);
        return vo;
    }

    /* ================= 提交 ================= */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScoreImportResultVO commit(Long userId, ScoreImportCommitDTO dto) {
        ClassInfo clazz = requireClass(userId);
        Exam exam = requireExam(clazz, dto == null ? null : dto.getExamId());

        List<String> subjects = SubjectUtil.split(exam.getSubjects());
        Set<String> subjectSet = new HashSet<>(subjects);
        Map<String, BigDecimal> fullScore = SubjectUtil.parseFullScore(exam.getFullScore(), subjects);

        List<ScoreImportCommitDTO.Row> rows = dto.getRows();
        if (rows == null || rows.isEmpty()) {
            throw new BizException(1001, "没有可导入的成绩");
        }
        if (rows.size() > 300) {
            throw new BizException(1001, "一次最多导入 300 个学生，请分批");
        }

        // ---- 一次性核对 studentId 的班级归属 ----
        // 别在循环里逐个 selectById，那是 N+1
        Set<Long> ids = new LinkedHashSet<>();
        for (ScoreImportCommitDTO.Row r : rows) {
            if (r.getStudentId() == null) {
                throw new BizException(1001, "第 " + r.getExcelRowNo() + " 行没匹配到学生");
            }
            ids.add(r.getStudentId());
        }
        Set<Long> inClass = new HashSet<>();
        for (Student s : studentMapper.selectList(new LambdaQueryWrapper<Student>()
                .eq(Student::getClassId, clazz.getId())
                .in(Student::getId, ids))) {
            inClass.add(s.getId());
        }
        for (Long id : ids) {
            if (!inClass.contains(id)) {
                throw new BizException(3002, "有学生不属于你的班，请重新预览");
            }
        }

        // 已有的分数。用来判断「这格是新增还是覆盖」，也用来跳过「空对空」
        Map<Long, Map<String, BigDecimal>> existing = existingScores(clazz.getId(), exam.getId());

        List<Score> flat = new ArrayList<>();
        Set<Long> touched = new HashSet<>();
        int createN = 0;
        int overwriteN = 0;
        int clearN = 0;

        for (ScoreImportCommitDTO.Row r : rows) {
            Map<String, BigDecimal> sc = r.getScores();
            if (sc == null || sc.isEmpty()) {
                continue;
            }
            Map<String, BigDecimal> old = existing.getOrDefault(r.getStudentId(), Map.of());

            for (Map.Entry<String, BigDecimal> e : sc.entrySet()) {
                String subject = e.getKey();
                if (!subjectSet.contains(subject)) {
                    // 前端传了本次考试没有的科目：说明数据和界面不同步了，
                    // 与其静默丢弃，不如整批拒绝 —— 静默丢弃会让老师以为导成功了
                    throw new BizException(1001, "科目「" + subject + "」不在本次考试里，请重新预览");
                }
                BigDecimal v = e.getValue();
                BigDecimal fs = fullScore.getOrDefault(subject, DEFAULT_FULL);

                if (v != null) {
                    if (v.compareTo(BigDecimal.ZERO) < 0) {
                        throw new BizException(1001, "「" + subject + "」分数不能为负");
                    }
                    if (v.compareTo(fs) > 0) {
                        throw new BizException(1001,
                                "「" + subject + "」超过满分 " + fmt(fs) + "，请重新预览");
                    }
                } else if (old.get(subject) == null) {
                    // 空对空，没有可写的东西
                    continue;
                }

                Score s = new Score();
                s.setClassId(clazz.getId());
                s.setExamId(exam.getId());
                s.setStudentId(r.getStudentId());
                s.setSubject(subject);
                s.setScore(v);          // null = 缺考，会照样写进去
                s.setFullScore(fs);
                flat.add(s);
                touched.add(r.getStudentId());

                if (v == null) {
                    clearN++;
                } else if (old.get(subject) != null) {
                    overwriteN++;
                } else {
                    createN++;
                }
            }
        }

        if (flat.isEmpty()) {
            throw new BizException(1001, "没有可写入的成绩（表格里全是空格）");
        }

        // 一条 SQL 写完。方法上有 @Transactional，中途异常整批回滚
        scoreMapper.batchUpsert(flat);

        ScoreImportResultVO result = new ScoreImportResultVO();
        result.setSaved(flat.size());
        result.setStudents(touched.size());
        result.setCreate(createN);
        result.setOverwrite(overwriteN);
        result.setClear(clearN);
        return result;
    }

    /* ================= 列 → 科目 ================= */

    /** 列映射的结果，打包返回，省得四个参数到处传 */
    private static final class ColumnPlan {
        List<ScoreImportPreviewVO.ColumnVO> columns = new ArrayList<>();
        /** 科目 → 列号。用 LinkedHashMap 保证按考试里配的科目顺序 */
        Map<String, Integer> subjectCol = new LinkedHashMap<>();
        /** 表里有、但没用上的列名 */
        List<String> unmapped = new ArrayList<>();
        /** 考试有、表里没有的科目 */
        List<String> missing = new ArrayList<>();
    }

    private ColumnPlan mapColumns(HeaderMapping map, List<String> subjects) {
        ColumnPlan plan = new ColumnPlan();

        // 归一化后的科目名 → 原始科目名。归一化是为了让「语文成绩」也能对上「语文」
        Map<String, String> subKey = new LinkedHashMap<>();
        for (String s : subjects) {
            String k = normalizeSubject(s);
            if (k != null) {
                subKey.put(k, s);
            }
        }

        // 按列号排序，预览里从左到右和 Excel 一致
        List<Map.Entry<Integer, String>> entries = new ArrayList<>(map.getHeaderTexts().entrySet());
        entries.sort(Map.Entry.comparingByKey());

        for (Map.Entry<Integer, String> e : entries) {
            int col = e.getKey();
            String header = e.getValue();

            ScoreImportPreviewVO.ColumnVO vo = new ScoreImportPreviewVO.ColumnVO();
            vo.setColumn(col);
            vo.setHeader(header);
            vo.setMapped(false);
            plan.columns.add(vo);

            // 先看是不是姓名 / 学号 / 班级 —— 这几列不参与科目匹配
            ImportField field = fieldOfColumn(map, col);
            if (field != null) {
                vo.setField(field.name());
                vo.setFieldLabel(field.getLabel());
                vo.setMapped(true);
                continue;
            }

            // 再试科目
            String key = normalizeSubject(header);
            String subject = key == null ? null : subKey.get(key);
            if (subject == null) {
                plan.unmapped.add(header);
                continue;
            }
            // 同一个科目认到两列（「语文」和「语文成绩」并存）时只取第一列，
            // 第二列落进 unmapped，老师在预览里能看到「这列没用上」
            if (plan.subjectCol.containsKey(subject)) {
                plan.unmapped.add(header);
                continue;
            }
            plan.subjectCol.put(subject, col);
            vo.setSubject(subject);
            vo.setMapped(true);
        }

        for (String s : subjects) {
            if (!plan.subjectCol.containsKey(s)) {
                plan.missing.add(s);
            }
        }
        return plan;
    }

    /**
     * 表头 → 科目名的比对键。
     *
     * <p>处理这几种真实写法：{@code 语文}、{@code 语文成绩}、{@code 语文（150分）}、
     * {@code *语文*}、{@code 语 文}、{@code 语文得分}。
     * 它们都该归一成 {@code 语文}。
     *
     * <p><b>不做模糊匹配</b>（比如「语」也算语文）：科目名都很短，
     * 模糊匹配会把「语文」和「语文综合」判成同一科，
     * 那比「认不出来、让老师改列名」糟糕得多。
     */
    private String normalizeSubject(String raw) {
        String s = TextNormalize.text(raw);
        if (s == null) {
            return null;
        }
        s = s.replace("*", "").replace("＊", "")
                .replace("：", "").replace(":", "");
        // 去掉括号注释：「语文（150分）」「数学(闭卷)」
        s = s.replaceAll("[（(][^）)]{0,12}[）)]", "");
        // 去掉所有空白：「语 文」在排版时很常见
        s = s.replaceAll("\\s+", "");
        for (String suf : SUBJECT_SUFFIX) {
            if (s.length() > suf.length() && s.endsWith(suf)) {
                s = s.substring(0, s.length() - suf.length());
                break;
            }
        }
        return s.isEmpty() ? null : s;
    }

    private ImportField fieldOfColumn(HeaderMapping map, int column) {
        for (Map.Entry<ImportField, Integer> e : map.getColumns().entrySet()) {
            if (e.getValue() == column) {
                return e.getKey();
            }
        }
        return null;
    }

    /* ================= 学生匹配 ================= */

    /**
     * 这一行是谁。
     *
     * <p>规则：<b>有学号就只认学号，没学号才按姓名</b>。
     * 学号填了但对不上，不退回按姓名匹配 —— 那会把一个填错学号的行
     * 悄悄导到同名的人头上，而老师看到的提示是「成功」。
     *
     * <p>重名必须报错，不能挑第一个：一个班两个「张伟」很常见，
     * 猜错就是把分数记到别人头上，而且事后极难发现。
     */
    private Student matchStudent(ScoreImportPreviewVO.RowVO row,
                                 Map<String, Student> byNo,
                                 Map<String, List<Student>> byName,
                                 List<String> errors) {
        String no = row.getStudentNo();
        if (no != null) {
            Student s = byNo.get(no);
            if (s == null) {
                errors.add("学号 " + no + " 不在本班");
            }
            return s;
        }

        String name = row.getName();
        if (name == null) {
            errors.add("姓名为空");
            return null;
        }
        List<Student> list = byName.get(name);
        if (list == null || list.isEmpty()) {
            errors.add("本班没有叫「" + name + "」的学生");
            return null;
        }
        if (list.size() > 1) {
            errors.add("本班有 " + list.size() + " 个「" + name + "」，请在表里填学号");
            return null;
        }
        return list.get(0);
    }

    /**
     * 本班全部学生，按学号索引。
     *
     * <p>不带 status 条件：已经离班的学生也可能出现在这次考试的成绩单里
     * （考完才走的）。把他排除掉，那几行就会报「学号不在本班」，
     * 老师只能删掉这些行才能导入，等于逼他丢数据。
     */
    private Map<String, Student> studentsByNo(Long classId) {
        Map<String, Student> map = new LinkedHashMap<>();
        for (Student s : allStudents(classId)) {
            if (s.getStudentNo() != null) {
                map.putIfAbsent(s.getStudentNo(), s);
            }
        }
        return map;
    }

    private Map<String, List<Student>> studentsByName(Long classId) {
        Map<String, List<Student>> map = new HashMap<>();
        for (Student s : allStudents(classId)) {
            String n = TextNormalize.text(s.getName());
            if (n != null) {
                map.computeIfAbsent(n, k -> new ArrayList<>()).add(s);
            }
        }
        return map;
    }

    private List<Student> allStudents(Long classId) {
        return studentMapper.selectList(
                new LambdaQueryWrapper<Student>().eq(Student::getClassId, classId));
    }

    /** studentId → (科目 → 分数)。已经存成缺考（null）的也在里面，值是 null */
    private Map<Long, Map<String, BigDecimal>> existingScores(Long classId, Long examId) {
        List<Score> list = scoreMapper.selectList(new LambdaQueryWrapper<Score>()
                .eq(Score::getExamId, examId)
                .eq(Score::getClassId, classId));
        Map<Long, Map<String, BigDecimal>> map = new HashMap<>();
        for (Score s : list) {
            map.computeIfAbsent(s.getStudentId(), k -> new HashMap<>())
                    .put(s.getSubject(), s.getScore());
        }
        return map;
    }

    /* ================= 单元格 ================= */

    private static final class CellResult {
        /** 分数；null 表示缺考 */
        BigDecimal value;
        /** 非 null 表示这一格填得不对 */
        String error;
    }

    /**
     * 一格成绩。
     *
     * <p>三种结局：
     * <ul>
     *   <li>空 / 「缺考」/ 「-」 → {@code value = null}，表示这一科没考</li>
     *   <li>合法数字 → {@code value = 分数}</li>
     *   <li>认不出来 → {@code error}，整行会被判错</li>
     * </ul>
     *
     * <p>为什么「认不出来」要判错而不是当缺考：把「85分（补考）」这种格子
     * 静默当成缺考，老师会以为导成功了，实际丢了一个分数。
     * 报错让他回去改一格，成本低得多。
     */
    private CellResult parseCell(String raw, BigDecimal full) {
        CellResult r = new CellResult();
        String s = TextNormalize.text(raw);
        if (s == null) {
            return r;
        }
        if (ABSENT.contains(s) || ABSENT.contains(s.toLowerCase(Locale.ROOT))) {
            return r;
        }

        // 「85分」这种带单位的写法，去掉单位再试
        String num = s;
        if (num.length() > 1 && num.endsWith("分")) {
            num = num.substring(0, num.length() - 1).trim();
        }

        BigDecimal v;
        try {
            v = new BigDecimal(num);
        } catch (NumberFormatException e) {
            r.error = "「" + s + "」不是数字";
            return r;
        }

        if (v.compareTo(BigDecimal.ZERO) < 0) {
            r.error = "不能为负";
            return r;
        }
        if (full != null && v.compareTo(full) > 0) {
            r.error = "超过满分 " + fmt(full);
            return r;
        }
        r.value = v;
        return r;
    }

    /* ================= 工具 ================= */

    private ClassInfo requireClass(Long userId) {
        classService.requireTeacher(userId);
        ClassInfo clazz = classService.myClass(userId);
        if (clazz == null) {
            throw new BizException(1001, "你还没有绑定班级，不能导入成绩");
        }
        return clazz;
    }

    private Exam requireExam(ClassInfo clazz, Long examId) {
        if (examId == null) {
            throw new BizException(1001, "缺少 examId");
        }
        Exam exam = examMapper.selectById(examId);
        if (exam == null) {
            throw new BizException(1004, "考试不存在");
        }
        // 和 ScoreServiceImpl 一样区分 1004 和 3002：
        // 混用会让前端以为「这次考试被删了」，从而把它从列表里移除
        if (!exam.getClassId().equals(clazz.getId())) {
            throw new BizException(3002, "这次考试不属于你的班");
        }
        return exam;
    }

    private String cellOf(SheetData sheet, int i, HeaderMapping map, ImportField field) {
        int col = map.columnOf(field);
        return col < 0 ? null : sheet.get(i, col);
    }

    private String fmt(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }

    /** 去掉路径，只留文件名。有些客户端会传 "C:\\fakepath\\成绩.xlsx" */
    private String baseName(String name) {
        if (name == null) {
            return "";
        }
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        return slash >= 0 ? name.substring(slash + 1) : name;
    }
}
