package com.banxing.classassistant.util;

/**
 * 文本归一化：把「人随手填的」「Excel 自动转的」各种写法收敛成一种。
 *
 * <p>这个类存在的理由是一份真实的名单：同一个班的班级名出现了 10 种写法
 * （{@code 628班} / {@code 628 班} / {@code 628（1）班} / {@code 628(1)班} …），
 * 学号一半是文本一半是数字，性别写「男/女」而不是 1/2。
 * 不归一化的话，导入要么全被判成「不属于本班」，要么学号带小数点。
 *
 * <p><b>归一化只用于「比较」和「入库」，不用于显示。</b>
 * 老师填了「628 班」，存进去还是「628 班」——只是比对时两边都变成「628」。
 * 擅自改用户输入的展示形式，会让人觉得数据被动了手脚。
 */
public final class TextNormalize {

    private TextNormalize() {
    }

    /**
     * 通用文本：去首尾空白，全角空格转半角，空串转 null。
     *
     * <p>为什么要处理全角空格（U+3000）：中文输入法下按空格很容易打出全角，
     * 而 {@code String.trim()} 只去 U+0020 及以下，{@code "\u3000".trim()} 还是 "\u3000"。
     * 这类「看不见的字符」是导入最常踩的坑 —— 明明看着是空的，却判成「已填写」。
     */
    public static String text(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.replace('\u3000', ' ').replace('\u00A0', ' ').trim();
        return s.isEmpty() ? null : s;
    }

    /**
     * 学号：统一按字符串处理。
     *
     * <p>三条规则：
     * <ol>
     *   <li>去首尾空白（Excel 里手输的学号经常带尾空格）</li>
     *   <li>去掉零宽字符（U+200B~U+200D、U+FEFF）—— 从网页或聊天记录复制粘贴带出来的，
     *       肉眼看不见，但会让「20280001」和「20280001」判成两个不同的学号</li>
     *   <li>空串转 null，和 {@code StudentServiceImpl.trimToNull} 保持同一个口径</li>
     * </ol>
     *
     * <p><b>不做前导零补齐。</b>不同学校的学号规则不一样，有的 8 位有的 12 位，
     * 猜错位数会把「001」补成「00000001」，反而更难查。
     */
    public static String studentNo(String raw) {
        String s = text(raw);
        if (s == null) {
            return null;
        }
        s = s.replaceAll("[\\u200B-\\u200D\\uFEFF]", "").trim();
        return s.isEmpty() ? null : s;
    }

    /**
     * 班级名归一化，<b>只用于比较，不用于入库</b>。
     *
     * <p>规则：全角括号转半角 → 去掉所有空白 → 去掉末尾的「班」字。
     * 于是下面这些全部变成 {@code "628"}：
     * <pre>
     * 628班   628 班   628 （班）   ６２８班？
     * </pre>
     * 而 {@code 628（1）班} 和 {@code 628(1)班} 都变成 {@code "628(1)"}。
     *
     * <p>为什么不做得更聪明（比如模糊匹配、编辑距离）：
     * 班级名很短，模糊匹配很容易把「628班」和「629班」判成同一个 ——
     * 那才是真正的灾难：老师以为在导自己班，实际把别人班的学生导进来了。
     * <b>宁可漏匹配让人手动确认，也不能错匹配。</b>
     */
    public static String className(String raw) {
        String s = text(raw);
        if (s == null) {
            return null;
        }
        s = s.replace('（', '(')
                .replace('）', ')')
                .replaceAll("\\s+", "");
        if (s.endsWith("班")) {
            s = s.substring(0, s.length() - 1);
        }
        return s.isEmpty() ? null : s;
    }

    /**
     * 两个班级名是不是同一个班。
     *
     * <p>两边都归一化后比较；任一边为空就当「无法判断」，返回 true ——
     * 名单里没填班级不该导致整张表导不进来。
     */
    public static boolean sameClass(String a, String b) {
        String na = className(a);
        String nb = className(b);
        if (na == null || nb == null) {
            return true;
        }
        return na.equals(nb);
    }

    /**
     * 性别：把「男/女/1/2/M/F」统一成 0/1/2。
     *
     * <p>认不出来的返回 0（未知），不报错 —— 名单里性别写错不值得整行失败，
     * 老师事后补一下就行。
     */
    public static Integer gender(String raw) {
        String s = text(raw);
        if (s == null) {
            return 0;
        }
        String lower = s.toLowerCase();
        if ("男".equals(s) || "1".equals(s) || "m".equals(lower) || "male".equals(lower)) {
            return 1;
        }
        if ("女".equals(s) || "2".equals(s) || "f".equals(lower) || "female".equals(lower)) {
            return 2;
        }
        return 0;
    }
}
