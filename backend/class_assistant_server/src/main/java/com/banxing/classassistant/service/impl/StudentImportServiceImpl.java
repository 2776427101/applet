package com.banxing.classassistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.banxing.classassistant.common.BizException;
import com.banxing.classassistant.dto.ImportCommitDTO;
import com.banxing.classassistant.dto.ImportPreviewVO;
import com.banxing.classassistant.dto.ImportResultVO;
import com.banxing.classassistant.dto.ImportRowDTO;
import com.banxing.classassistant.entity.ClassInfo;
import com.banxing.classassistant.entity.Student;
import com.banxing.classassistant.importer.HeaderMapping;
import com.banxing.classassistant.importer.ImportField;
import com.banxing.classassistant.importer.ParserRegistry;
import com.banxing.classassistant.importer.SheetData;
import com.banxing.classassistant.importer.WorkbookParser;
import com.banxing.classassistant.mapper.StudentMapper;
import com.banxing.classassistant.service.ClassService;
import com.banxing.classassistant.service.StudentImportService;
import com.banxing.classassistant.util.TextNormalize;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 学生名单导入实现。
 *
 * <p>整个流程分两段，边界很清楚：
 * <pre>
 * preview: 文件 → 解析 → 表头识别 → 逐行校验 → 和库比对 → 返回「会发生什么」
 * commit : 前端回传的行 → 重新校验 → 写库 → 返回「实际发生了什么」
 * </pre>
 *
 * <p><b>为什么 commit 要重新校验一遍</b>：预览和提交之间可能隔了几分钟，
 * 期间别的老师可能加了一个同学号的学生；而且前端传的数据本来就不可信。
 * 校验逻辑两个方法共用（{@link #validate}），所以不会出现「预览说能导、提交却失败」。
 */
@Service
@RequiredArgsConstructor
public class StudentImportServiceImpl implements StudentImportService {

    /** 上传上限。69 人的名单 xlsx 大概 20KB，5MB 已经非常宽松 */
    private static final long MAX_FILE_BYTES = 5L * 1024 * 1024;

    /** 生日能认出来的几种写法。Excel 里手输的生日格式五花八门 */
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-M-d"),
            DateTimeFormatter.ofPattern("yyyy/M/d"),
            DateTimeFormatter.ofPattern("yyyy.M.d"),
            DateTimeFormatter.ofPattern("yyyyMMdd"),
            DateTimeFormatter.ofPattern("yyyy年M月d日"));

    private final ClassService classService;
    private final StudentMapper studentMapper;
    private final ParserRegistry parserRegistry;

    /* ================= 预览 ================= */

    @Override
    public ImportPreviewVO preview(Long userId, MultipartFile file) {
        ClassInfo clazz = requireClass(userId);

        if (file == null || file.isEmpty()) {
            throw new BizException(1001, "请选择要导入的文件");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new BizException(1001, "文件超过 5MB，名单一般只有几十 KB，请确认没传错文件");
        }

        // 这一步会把 .xls / .csv / .txt 拦下来，并给出「请另存为 .xlsx」的提示
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
                            + "请确认表格里有一行表头，且其中一列叫「姓名」");
        }

        // 本班已有学生的学号 → 学生。含已离班的：学号是终身的，离班的人还占着号
        Map<String, Student> existing = existingByNo(clazz.getId());
        Map<String, Integer> seenNo = new HashMap<>();

        ImportPreviewVO vo = new ImportPreviewVO();
        vo.setSheetName(sheet.getSheetName());
        vo.setHeaderRowNo(map.getExcelRowNo());
        vo.setBlankRows(sheet.getBlankRows());
        vo.setColumns(buildColumns(map));
        vo.setUnmappedHeaders(unmappedHeaders(map));
        vo.setClassName(clazz.getName());

        // 名单里出现过的「别的班」。用 LinkedHashSet 保序：
        // 老师看提示时希望顺序和表格里出现的顺序一致，不是随机排
        Set<String> otherNames = new LinkedHashSet<>();

        List<ImportRowDTO> rows = new ArrayList<>();
        ImportPreviewVO.StatsVO stats = new ImportPreviewVO.StatsVO();

        // 从表头的下一行开始读。表头之前的行（大标题、空行）直接跳过
        for (int i = map.getRowIndex() + 1; i < sheet.size(); i++) {
            if (sheet.isBlankRow(i)) {
                continue;
            }
            // 班级原文只在预览时有（commit 传回来的行里没有这一列），
            // 所以校验时当参数传，不塞进 DTO
            String rawClassName = TextNormalize.text(cell(sheet, i, map, ImportField.CLASS_NAME));

            ImportRowDTO row = readRow(sheet, i, map);
            List<String> errors = validate(row, clazz, rawClassName);

            // 记下对不上的班名，最多留 5 个。再多也没意义 ——
            // 名单跨了十几个班的话，老师该做的是先拆表，不是看这行提示
            if (rawClassName != null && !rawClassName.isEmpty()
                    && !TextNormalize.sameClass(rawClassName, clazz.getName())
                    && otherNames.size() < 5) {
                otherNames.add(rawClassName);
            }

            String no = row.getStudentNo();
            if (no != null) {
                Integer first = seenNo.putIfAbsent(no, row.getExcelRowNo());
                if (first != null) {
                    errors.add("学号 " + no + " 和第 " + first + " 行重复");
                }
            }

            if (!errors.isEmpty()) {
                row.setAction("error");
                row.setErrors(errors);
                row.setReason(errors.get(0));
                stats.setError(stats.getError() + 1);
            } else if (no != null && existing.containsKey(no)) {
                Student old = existing.get(no);
                row.setAction("duplicate");
                row.setReason("学号 " + no + " 已存在（" + old.getName() + "）");
                stats.setDuplicate(stats.getDuplicate() + 1);
            } else {
                row.setAction("create");
                stats.setCreate(stats.getCreate() + 1);
                if (no == null) {
                    // 没学号就没法判断是不是重复，只能一律新增。
                    // 这个数字要显眼地告诉老师，否则导两次就多出一倍的人
                    stats.setNoStudentNo(stats.getNoStudentNo() + 1);
                }
            }
            rows.add(row);
        }

        stats.setImportable(stats.getCreate() + stats.getDuplicate());
        vo.setDataRows(rows.size());
        vo.setStats(stats);
        vo.setRows(rows);
        vo.setOtherClassNames(new ArrayList<>(otherNames));
        return vo;
    }

    /* ================= 提交 ================= */

    @Override
    public ImportResultVO commit(Long userId, ImportCommitDTO dto) {
        ClassInfo clazz = requireClass(userId);
        boolean updateMode = "update".equals(dto.getMode());

        Map<String, Student> existing = existingByNo(clazz.getId());
        Map<String, Integer> seenNo = new HashMap<>();
        ImportResultVO result = new ImportResultVO();

        for (ImportRowDTO row : dto.getRows()) {
            // 前端传来的 action 一概不看，重新判一遍。
            // rawClassName 传 null：DTO 里本来就没有班级，而且预览阶段已经拦过别的班的人了
            List<String> errors = validate(row, clazz, null);
            String no = row.getStudentNo();
            if (no != null) {
                Integer first = seenNo.putIfAbsent(no, row.getExcelRowNo());
                if (first != null) {
                    errors.add("学号 " + no + " 和第 " + first + " 行重复");
                }
            }
            if (!errors.isEmpty()) {
                result.addFailure(row.getExcelRowNo(), row.getName(), String.join("；", errors));
                continue;
            }

            Student old = no == null ? null : existing.get(no);
            if (old != null && !updateMode) {
                // skip 模式：库里那 5 个人一个字都不动
                result.setSkipped(result.getSkipped() + 1);
                continue;
            }

            try {
                if (old != null) {
                    updateOne(clazz.getId(), old.getId(), row);
                    result.setUpdated(result.getUpdated() + 1);
                } else {
                    insertOne(clazz.getId(), row);
                    result.setCreated(result.getCreated() + 1);
                }
            } catch (Exception e) {
                // 唯一键冲突是这里最常见的异常：预览之后、提交之前，
                // 可能有别人刚录入了同一个学号。单行失败不该让整批回滚
                result.addFailure(row.getExcelRowNo(), row.getName(), friendly(e));
            }
        }
        return result;
    }

    /* ================= 读一行 ================= */

    /**
     * 把 Excel 的一行读成 {@link ImportRowDTO}。
     *
     * <p>只做「取值 + 归一化」，不做判断 —— 校验统一放在 {@link #validate}，
     * 因为 commit 拿到的是前端回传的 DTO，没有 Excel 可读，
     * 两个入口必须共用同一份校验规则。
     */
    private ImportRowDTO readRow(SheetData sheet, int i, HeaderMapping map) {
        ImportRowDTO row = new ImportRowDTO();
        row.setExcelRowNo(sheet.excelRowNo(i));

        row.setName(TextNormalize.text(cell(sheet, i, map, ImportField.NAME)));
        row.setStudentNo(TextNormalize.studentNo(cell(sheet, i, map, ImportField.STUDENT_NO)));
        row.setGender(TextNormalize.gender(cell(sheet, i, map, ImportField.GENDER)));
        row.setBirthday(parseDate(cell(sheet, i, map, ImportField.BIRTHDAY)));
        row.setDormitory(TextNormalize.text(cell(sheet, i, map, ImportField.DORMITORY)));
        row.setPhone(TextNormalize.text(cell(sheet, i, map, ImportField.PHONE)));
        row.setWechat(TextNormalize.text(cell(sheet, i, map, ImportField.WECHAT)));
        row.setFamilyPhone1(TextNormalize.text(cell(sheet, i, map, ImportField.FAMILY_PHONE1)));
        row.setFamilyPhone2(TextNormalize.text(cell(sheet, i, map, ImportField.FAMILY_PHONE2)));
        row.setTrainingDirection(TextNormalize.text(cell(sheet, i, map, ImportField.TRAINING_DIRECTION)));
        row.setNation(TextNormalize.text(cell(sheet, i, map, ImportField.NATION)));
        row.setPoliticalStatus(TextNormalize.text(cell(sheet, i, map, ImportField.POLITICAL_STATUS)));
        row.setPartyActivist(TextNormalize.text(cell(sheet, i, map, ImportField.PARTY_ACTIVIST)));
        return row;
    }

    private String cell(SheetData sheet, int i, HeaderMapping map, ImportField field) {
        int col = map.columnOf(field);
        return col < 0 ? null : sheet.get(i, col);
    }

    /* ================= 校验 ================= */

    /**
     * 一行数据的所有问题。
     *
     * <p><b>一次性报全，不要发现一个就 return</b>：
     * 老师改完姓名再提交、又被告知学号重复，来回几趟会骂人。
     *
     * @param rawClassName 这一行在 Excel 里写的班级原文。只有预览时能拿到，
     *                     提交时传 null 表示「这项不用查」
     */
    private List<String> validate(ImportRowDTO row, ClassInfo clazz, String rawClassName) {
        List<String> errors = new ArrayList<>();

        if (row.getName() == null) {
            errors.add("姓名为空");
        } else if (row.getName().length() > 32) {
            errors.add("姓名超过 32 个字");
        }

        if (row.getStudentNo() != null && row.getStudentNo().length() > 32) {
            errors.add("学号超过 32 个字");
        }

        checkLength(errors, "宿舍", row.getDormitory(), 32);
        checkLength(errors, "手机号", row.getPhone(), 20);
        checkLength(errors, "微信号", row.getWechat(), 64);
        checkLength(errors, "家人电话1", row.getFamilyPhone1(), 20);
        checkLength(errors, "家人电话2", row.getFamilyPhone2(), 20);
        checkLength(errors, "培训方向", row.getTrainingDirection(), 64);
        checkLength(errors, "民族", row.getNation(), 32);
        checkLength(errors, "政治面貌", row.getPoliticalStatus(), 32);
        checkLength(errors, "入党积极分子", row.getPartyActivist(), 32);

        // 班级对不上：这一行大概率是别的班的学生。
        // 真实名单里 69 个人跨 9 个班，直接全导进 628 班是灾难
        if (rawClassName != null && !TextNormalize.sameClass(rawClassName, clazz.getName())) {
            errors.add("班级是「" + rawClassName + "」，不是本班「" + clazz.getName() + "」");
        }

        return errors;
    }

    private void checkLength(List<String> errors, String label, String value, int max) {
        if (value != null && value.length() > max) {
            errors.add(label + "超过 " + max + " 个字");
        }
    }

    /* ================= 写库 ================= */

    private void insertOne(Long classId, ImportRowDTO row) {
        Student s = new Student();
        s.setClassId(classId);
        s.setName(row.getName());
        s.setGender(row.getGender() == null ? 0 : row.getGender());
        s.setBirthday(row.getBirthday());
        s.setStudentNo(row.getStudentNo());
        s.setDormitory(row.getDormitory());
        s.setPhone(row.getPhone());
        s.setWechat(row.getWechat());
        s.setFamilyPhone1(row.getFamilyPhone1());
        s.setFamilyPhone2(row.getFamilyPhone2());
        s.setTrainingDirection(row.getTrainingDirection());
        s.setNation(row.getNation());
        s.setPoliticalStatus(row.getPoliticalStatus());
        s.setPartyActivist(row.getPartyActivist());
        s.setStatus(1);
        studentMapper.insert(s);
    }

    /**
     * 覆盖更新。
     *
     * <p><b>不更新 status</b>：名单里没有「在读/离班」这一列，
     * 一个已离班的学生被重新导进来，不该因为「这次名单里有他」就自动复学。
     * 复学是另一个动作，得老师明确点。
     *
     * <p>也不更新 classId：导入的目标班永远是 token 对应的班，
     * 前端传什么都不改这个事实。
     */
    private void updateOne(Long classId, Long studentId, ImportRowDTO row) {
        studentMapper.update(null, new LambdaUpdateWrapper<Student>()
                .eq(Student::getId, studentId)
                .eq(Student::getClassId, classId)
                .set(Student::getName, row.getName())
                .set(Student::getGender, row.getGender() == null ? 0 : row.getGender())
                .set(Student::getBirthday, row.getBirthday())
                .set(Student::getDormitory, row.getDormitory())
                .set(Student::getPhone, row.getPhone())
                .set(Student::getWechat, row.getWechat())
                .set(Student::getFamilyPhone1, row.getFamilyPhone1())
                .set(Student::getFamilyPhone2, row.getFamilyPhone2())
                .set(Student::getTrainingDirection, row.getTrainingDirection())
                .set(Student::getNation, row.getNation())
                .set(Student::getPoliticalStatus, row.getPoliticalStatus())
                .set(Student::getPartyActivist, row.getPartyActivist()));
    }

    /* ================= 工具 ================= */

    private ClassInfo requireClass(Long userId) {
        classService.requireTeacher(userId);
        ClassInfo clazz = classService.myClass(userId);
        if (clazz == null) {
            throw new BizException(1001, "你还没有绑定班级，不能导入学生");
        }
        return clazz;
    }

    /**
     * 本班所有已有学生，按学号索引。
     *
     * <p>一次查完再在内存里比对，而不是每行查一次库：
     * 69 行就是 69 次 round trip，慢且没必要。
     *
     * <p>不带 status 条件 —— 学号唯一性的范围包含已离班的学生。
     */
    private Map<String, Student> existingByNo(Long classId) {
        List<Student> list = studentMapper.selectList(new LambdaQueryWrapper<Student>()
                .eq(Student::getClassId, classId)
                .isNotNull(Student::getStudentNo));
        Map<String, Student> map = new LinkedHashMap<>();
        for (Student s : list) {
            if (s.getStudentNo() != null) {
                map.putIfAbsent(s.getStudentNo(), s);
            }
        }
        return map;
    }

    /**
     * 把 Excel 里各种生日写法统一成 {@code LocalDate}。
     *
     * <p>能认的：{@code 2009-03-12}、{@code 2009/3/12}、{@code 2009.3.12}、
     * {@code 20090312}、{@code 2009年3月12日}。
     * 认不出来返回 null —— 这一格就当作没填，不因为生日格式不对挡住整行导入。
     *
     * <p>为什么不报错：名单里 69 个人的生日有 3 种写法很常见，
     * 因为格式不一致就让人回去改 Excel，比丢掉一个生日字段麻烦得多。
     * 老师事后在编辑页补一个就行。
     */
    private LocalDate parseDate(String raw) {
        String s = TextNormalize.text(raw);
        if (s == null) {
            return null;
        }
        // "2009-03-12 00:00:00" 这种带时间的，砍掉后半截
        int space = s.indexOf(' ');
        if (space > 0) {
            s = s.substring(0, space);
        }
        for (DateTimeFormatter f : DATE_FORMATS) {
            try {
                return LocalDate.parse(s, f);
            } catch (Exception ignore) {
                // 换下一种格式
            }
        }
        return null;
    }

    private List<ImportPreviewVO.ColumnVO> buildColumns(HeaderMapping map) {
        List<ImportPreviewVO.ColumnVO> list = new ArrayList<>();
        // 按列下标排序，前端画出来才和 Excel 一致
        List<Map.Entry<Integer, String>> entries = new ArrayList<>(map.getHeaderTexts().entrySet());
        entries.sort(Map.Entry.comparingByKey());

        for (Map.Entry<Integer, String> e : entries) {
            ImportPreviewVO.ColumnVO col = new ImportPreviewVO.ColumnVO();
            col.setColumn(e.getKey());
            col.setHeader(e.getValue());

            ImportField field = fieldOfColumn(map, e.getKey());
            col.setMapped(field != null);
            if (field != null) {
                col.setField(field.name());
                col.setFieldLabel(field.getLabel());
            }
            list.add(col);
        }
        return list;
    }

    private ImportField fieldOfColumn(HeaderMapping map, int column) {
        for (Map.Entry<ImportField, Integer> e : map.getColumns().entrySet()) {
            if (e.getValue() == column) {
                return e.getKey();
            }
        }
        return null;
    }

    private List<String> unmappedHeaders(HeaderMapping map) {
        List<String> list = new ArrayList<>();
        for (Integer c : map.getUnmappedColumns()) {
            String text = map.getHeaderTexts().get(c);
            if (text != null) {
                list.add(text);
            }
        }
        return list;
    }

    /** 去掉路径，只留文件名。有些客户端会传 "C:\\fakepath\\名单.xlsx" */
    private String baseName(String name) {
        if (name == null) {
            return "";
        }
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        return slash >= 0 ? name.substring(slash + 1) : name;
    }

    /** 把数据库异常翻译成老师看得懂的话 */
    private String friendly(Exception e) {
        String msg = e.getMessage() == null ? "" : e.getMessage();
        if (msg.contains("uk_class_student_no") || msg.contains("Duplicate entry")) {
            return "学号在本班已存在（可能有别人同时导入），请刷新后重试";
        }
        return "写入失败：" + (msg.isEmpty() ? e.getClass().getSimpleName() : msg);
    }
}
