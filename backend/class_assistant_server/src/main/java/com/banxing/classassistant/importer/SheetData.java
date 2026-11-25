package com.banxing.classassistant.importer;

import java.util.ArrayList;
import java.util.List;

/**
 * 一个工作表解析出来的「纯文本矩阵」。
 *
 * <p>为什么解析层只产出字符串，而不是直接产出 {@code Student}：
 * 解析器不该知道「学生」是什么。它只负责把 xlsx 里那一堆 XML 变成人看得懂的行列，
 * 至于哪一列是姓名、哪一列是学号，是下一层（表头识别）的事。
 * 这样以后加「成绩表导入」，解析器一行都不用改。
 *
 * <p><b>空格子用 {@code null} 表示，不是空字符串。</b>
 * Excel 里「这一格没填」和「这一格填了个空格」在业务上是两件事，
 * 但在这里都归一成 null —— 归一化交给 TextNormalize，解析层不做业务判断。
 *
 * <p><b>为什么还要记 Excel 行号</b>：表格中间可能有整行空白（格式刷留下的），
 * 如果解析时把它们剔掉，数组下标就和 Excel 里看到的行号对不上了。
 * 老师看到「第 15 行学号重复」，去 Excel 一翻是第 18 行，就会以为系统错了。
 */
public class SheetData {

    /** 工作表名，用于预览页显示「来自 sheet「软通动力」」 */
    private final String sheetName;

    /** 行 × 列。行内长度可能不一致（Excel 允许某行少几列），取值时要判空 */
    private final List<List<String>> rows = new ArrayList<>();

    /** 与 rows 一一对应的 Excel 行号（1 开始），空行也占一个位置 */
    private final List<Integer> rowNos = new ArrayList<>();

    /** 解析时遇到的全空行数，用于在预览里说明「表里有 3 行是空行」 */
    private int blankRows;

    public SheetData(String sheetName) {
        this.sheetName = sheetName;
    }

    public void addRow(int excelRowNo, List<String> row) {
        rows.add(row);
        rowNos.add(excelRowNo);
    }

    public String getSheetName() {
        return sheetName;
    }

    public List<List<String>> getRows() {
        return rows;
    }

    public int size() {
        return rows.size();
    }

    /**
     * 第 i 行在 Excel 里的行号（1 开始）。
     *
     * <p>报错时统一用它，不要用 {@code i + 1} —— 表格中间有空行或删过行时，
     * 两者不是一回事。
     */
    public int excelRowNo(int i) {
        return i >= 0 && i < rowNos.size() ? rowNos.get(i) : i + 1;
    }

    /** 这一行是不是全空（导入时要跳过，但不能因此改变行号） */
    public boolean isBlankRow(int i) {
        if (i < 0 || i >= rows.size()) {
            return true;
        }
        for (String v : rows.get(i)) {
            if (v != null && !v.isBlank()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 取第 row 行第 col 列的值。
     *
     * <p>越界返回 null 而不是抛异常：Excel 里「这一行只有 3 列」是合法的，
     * 遍历 15 个表头时第 4~15 列本来就该是 null。
     */
    public String get(int row, int col) {
        if (row < 0 || row >= rows.size()) {
            return null;
        }
        List<String> r = rows.get(row);
        if (col < 0 || col >= r.size()) {
            return null;
        }
        return r.get(col);
    }

    /** 这张表最宽有几列 */
    public int maxCols() {
        int max = 0;
        for (List<String> r : rows) {
            max = Math.max(max, r.size());
        }
        return max;
    }

    public int getBlankRows() {
        return blankRows;
    }

    public void setBlankRows(int blankRows) {
        this.blankRows = blankRows;
    }
}
