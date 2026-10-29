package com.banxing.classassistant.importer;

import com.banxing.classassistant.util.TextNormalize;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 表头识别的结果：哪一行是表头、哪一列对应哪个字段。
 *
 * <p><b>「表头自动识别」怎么做的</b>：不是猜，是打分。
 * 拿前 10 行逐个当「表头候选」，每命中一个已知字段名就加一分，
 * 分最高且包含「姓名」的那一行就是表头行。
 *
 * <p>为什么必须包含「姓名」：任何一张学生名单都有姓名列。
 * 不加这个限制，一张只有「学号/语文/数学/英语」的成绩表会被当成名单，
 * 因为它也能命中「学号」这一列 —— 那就导进一堆没有名字的学生。
 */
public final class HeaderMapping {

    /** 往下找几行。够覆盖「第 1 行大标题、第 2 行表头」这种最常见的情况 */
    public static final int SCAN_ROWS = 10;

    /** 表头里出现这些词，说明是「家长/家人的电话」，不能当成学生本人的手机号 */
    private static final Pattern FAMILY_HINT = Pattern.compile("家长|家人|家庭|监护");

    /** 别名按长度降序试，长的更具体。「家人电话1」要先于「电话」被匹配到 */
    private static final List<Map.Entry<ImportField, String>> ALIASES = buildAliases();

    private final int rowIndex;
    private final int excelRowNo;
    private final Map<ImportField, Integer> columns;
    private final Map<Integer, String> headerTexts;
    private final List<Integer> unmappedColumns;

    private HeaderMapping(int rowIndex, int excelRowNo, Map<ImportField, Integer> columns,
                          Map<Integer, String> headerTexts, List<Integer> unmappedColumns) {
        this.rowIndex = rowIndex;
        this.excelRowNo = excelRowNo;
        this.columns = columns;
        this.headerTexts = headerTexts;
        this.unmappedColumns = unmappedColumns;
    }

    /* ================= 识别 ================= */

    /**
     * 在表格前 10 行里找表头。
     *
     * @return 识别结果；<b>找不到返回 null</b>（一行都没命中「姓名」）
     */
    public static HeaderMapping detect(SheetData sheet) {
        HeaderMapping best = null;
        int bestScore = 0;
        int limit = Math.min(SCAN_ROWS, sheet.size());

        for (int i = 0; i < limit; i++) {
            if (sheet.isBlankRow(i)) {
                continue;
            }
            List<String> row = sheet.getRows().get(i);

            Map<ImportField, Integer> cols = new LinkedHashMap<>();
            Map<Integer, String> texts = new LinkedHashMap<>();
            List<Integer> unmapped = new ArrayList<>();

            for (int c = 0; c < row.size(); c++) {
                String raw = row.get(c);
                String cleaned = cleanHeader(raw);
                if (cleaned == null) {
                    continue;
                }
                texts.put(c, raw.trim());

                ImportField field = match(cleaned);
                // 同一个字段认到两列时只取第一列（比如「联系电话」和「手机号」并存），
                // 后面的列会落进 unmapped，老师在预览里能看到「这列没用上」
                if (field != null && !cols.containsKey(field)) {
                    cols.put(field, c);
                } else {
                    unmapped.add(c);
                }
            }

            if (!cols.containsKey(ImportField.NAME)) {
                continue;
            }
            if (cols.size() > bestScore) {
                bestScore = cols.size();
                best = new HeaderMapping(i, sheet.excelRowNo(i), cols, texts, unmapped);
            }
        }
        return best;
    }

    /**
     * 表头文字清洗：去掉「*」「（必填）」「：」这些装饰。
     *
     * <p>Excel 里表头经常写成「姓名（必填）」「*学号*」「性别：」，
     * 不清洗的话精确匹配全失败，只能落到包含匹配，容易误判。
     */
    private static String cleanHeader(String raw) {
        String s = TextNormalize.text(raw);
        if (s == null) {
            return null;
        }
        s = s.replace("*", "").replace("＊", "")
                .replace("：", "").replace(":", "");
        // 去掉括号注释，但只去掉短的 —— 「（必填）」「(1)」会去掉，
        // 「（与身份证一致）」这种长说明也去掉，反正是装饰
        s = s.replaceAll("[（(][^）)]{0,12}[）)]", "");
        return TextNormalize.text(s);
    }

    /**
     * 表头 → 字段。
     *
     * <p>两轮：
     * <ol>
     *   <li><b>精确匹配</b>：整串相等。命中率最高也最安全</li>
     *   <li><b>包含匹配</b>：表头里含别名，且表头不比别名长太多。
     *       <b>跳过「姓名」</b> —— 「家长姓名」「班主任姓名」都含「姓名」，
     *       放进来会把家长名字当成学生名字，比「认不出来」糟糕得多</li>
     * </ol>
     */
    private static ImportField match(String header) {
        for (Map.Entry<ImportField, String> e : ALIASES) {
            if (e.getValue().equals(header)) {
                return e.getKey();
            }
        }

        boolean family = FAMILY_HINT.matcher(header).find();
        for (Map.Entry<ImportField, String> e : ALIASES) {
            ImportField field = e.getKey();
            if (field == ImportField.NAME) {
                continue;
            }
            // 「家长联系电话」里有「联系电话」，不加这条会被认成学生本人手机号
            if (family && field != ImportField.FAMILY_PHONE1 && field != ImportField.FAMILY_PHONE2) {
                continue;
            }
            String alias = e.getValue();
            if (header.contains(alias) && header.length() <= alias.length() + 6) {
                return field;
            }
        }
        return null;
    }

    private static List<Map.Entry<ImportField, String>> buildAliases() {
        List<Map.Entry<ImportField, String>> list = new ArrayList<>();
        for (ImportField f : ImportField.values()) {
            for (String alias : f.getAliases()) {
                list.add(Map.entry(f, alias));
            }
        }
        // 长度降序：先试「家人电话1」再试「家人电话」
        list.sort(Comparator.comparingInt((Map.Entry<ImportField, String> e) -> e.getValue().length()).reversed());
        return list;
    }

    /* ================= 取值 ================= */

    public int getRowIndex() {
        return rowIndex;
    }

    public int getExcelRowNo() {
        return excelRowNo;
    }

    public Map<ImportField, Integer> getColumns() {
        return columns;
    }

    public Map<Integer, String> getHeaderTexts() {
        return headerTexts;
    }

    public List<Integer> getUnmappedColumns() {
        return unmappedColumns;
    }

    public boolean has(ImportField field) {
        return columns.containsKey(field);
    }

    /** 这一列在不在表里。不在就返回 -1，取值为 null */
    public int columnOf(ImportField field) {
        Integer c = columns.get(field);
        return c == null ? -1 : c;
    }

    /**
     * 缺了哪些必填列。目前只有「姓名」是必填，
     * 但接口写成 List 是为了将来加必填字段时不用改调用方。
     */
    public List<String> missingRequired() {
        List<String> missing = new ArrayList<>();
        for (ImportField f : ImportField.values()) {
            if (f.isRequired() && !columns.containsKey(f)) {
                missing.add(f.getLabel());
            }
        }
        return missing;
    }
}
