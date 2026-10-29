package com.banxing.classassistant.importer;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXParseException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * XLSX 解析器，只用 JDK 自带的 zip + DOM，<b>不引入 Apache POI</b>。
 *
 * <p>为什么不用 POI：poi-ooxml 会带进 xmlbeans、commons-compress、log4j-api 等
 * 一堆传递依赖（解压后 20MB+），而我们只需要「读出第一张表的文字」这一件事。
 * xlsx 本质就是一个 zip 包，里面几个 XML，用 JDK 就能读。
 *
 * <p>代价是：图表、图片、公式、条件格式这些都不解析 —— 但导入名单用不到。
 * 如果第二期真要做「保留格式的导出」，那时候再换 POI 也不迟，
 * 因为上层只依赖 {@link WorkbookParser} 接口。
 *
 * <p>一份 xlsx 里我们只关心四个部件：
 * <pre>
 * xl/workbook.xml              哪个 sheet 叫什么、对应哪个 rId
 * xl/_rels/workbook.xml.rels   rId → worksheets/sheetN.xml
 * xl/sharedStrings.xml         所有文本单元格的内容池（单元格里只存下标）
 * xl/styles.xml                判断某个数字到底是「数字」还是「日期」
 * xl/worksheets/sheetN.xml     真正的行列数据
 * </pre>
 */
@Component
public class XlsxWorkbookParser implements WorkbookParser {

    /** 单个部件解压后的上限。正常 xlsx 每个 XML 都远小于这个值，超过基本可以判定是 zip bomb */
    private static final long MAX_ENTRY_BYTES = 32L * 1024 * 1024;
    /** 整个包解压后的上限 */
    private static final long MAX_TOTAL_BYTES = 64L * 1024 * 1024;

    /** Excel 的日期纪元。1900 年那套有个「1900-02-29」的历史 bug，用 1899-12-30 当基准正好抵消 */
    private static final LocalDate EXCEL_EPOCH = LocalDate.of(1899, 12, 30);

    private static final String NS_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";

    @Override
    public String extension() {
        return "xlsx";
    }

    @Override
    public String displayName() {
        return "Excel 2007+ (.xlsx)";
    }

    @Override
    public SheetData parse(InputStream in) throws IOException {
        Map<String, byte[]> parts = readZip(in);

        List<String> shared = parseSharedStrings(parts.get("xl/sharedStrings.xml"));
        Set<Integer> dateStyles = parseDateStyles(parts.get("xl/styles.xml"));
        SheetRef ref = firstSheet(parts);

        byte[] sheetXml = parts.get(ref.path);
        if (sheetXml == null) {
            throw new IOException("文件里找不到工作表「" + ref.name + "」的数据");
        }
        return parseSheet(ref.name, sheetXml, shared, dateStyles);
    }

    /* ================= 1. 解压 ================= */

    /**
     * 把整个 zip 读进内存。
     *
     * <p>为什么全读进来而不是边解压边解析：xlsx 的部件之间有依赖
     * （sheet1.xml 里的数字要靠 sharedStrings 和 styles 解释），
     * 而 zip 是顺序流，回头再取某个部件要重新解一遍。文件才几百 KB，全读最省事。
     *
     * <p>顺带做了两件事：限制解压体积（防 zip bomb）、
     * 读不到任何部件时给出「不是 xlsx」的明确提示（把 .xls 改名成 .xlsx 就会走到这里）。
     */
    private Map<String, byte[]> readZip(InputStream in) throws IOException {
        Map<String, byte[]> parts = new LinkedHashMap<>();
        long total = 0;
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(in))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                long size = 0;
                int n;
                while ((n = zis.read(buf)) > 0) {
                    size += n;
                    if (size > MAX_ENTRY_BYTES) {
                        throw new IOException("文件内容异常：单个部件解压后超过 32MB");
                    }
                    bos.write(buf, 0, n);
                }
                total += size;
                if (total > MAX_TOTAL_BYTES) {
                    throw new IOException("文件过大：解压后超过 64MB");
                }
                parts.put(entry.getName(), bos.toByteArray());
            }
        }
        if (parts.isEmpty()) {
            throw new IOException("这不是有效的 xlsx 文件。如果是老版 .xls，请先用 Excel 另存为 .xlsx");
        }
        return parts;
    }

    /* ================= 2. 共享字符串 ================= */

    /**
     * sharedStrings.xml 是所有文本单元格的内容池。
     *
     * <p>单元格里存的是下标（{@code t="s"} 时 {@code <v>3</v>} 表示第 3 个字符串），
     * 同一个名字出现 50 次也只在池子里存一份 —— 这是 xlsx 压体积的核心设计。
     */
    private List<String> parseSharedStrings(byte[] xml) throws IOException {
        List<String> list = new ArrayList<>();
        Document doc = parseXml(xml);
        if (doc == null) {
            return list;
        }
        NodeList items = doc.getElementsByTagNameNS("*", "si");
        for (int i = 0; i < items.getLength(); i++) {
            list.add(richText((Element) items.item(i)));
        }
        return list;
    }

    /**
     * 取 {@code <si>} 或 {@code <is>} 里的纯文本。
     *
     * <p>一个格子可能是「简单文本」{@code <t>张三</t>}，
     * 也可能是「富文本」{@code <r><t>张</t></r><r><t>三</t></r>}（Excel 里改过部分字体的都会变成这样）。
     * 只拼直接子节点，是为了跳过 {@code <rPh>}（日文注音）——
     * 它有 {@code <t>} 但不是正文，拼进去会变成「张三zhangsan」。
     */
    private String richText(Element parent) {
        StringBuilder sb = new StringBuilder();
        NodeList kids = parent.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            Node node = kids.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            String local = node.getLocalName();
            if ("t".equals(local)) {
                sb.append(node.getTextContent());
            } else if ("r".equals(local)) {
                NodeList ts = ((Element) node).getElementsByTagNameNS("*", "t");
                for (int j = 0; j < ts.getLength(); j++) {
                    sb.append(ts.item(j).getTextContent());
                }
            }
        }
        return sb.toString();
    }

    /* ================= 3. 样式 → 判断日期 ================= */

    /**
     * 算出「哪些样式下标代表日期格式」。
     *
     * <p>为什么需要这一步：Excel 里日期存的是一个数字（2023-03-15 存成 45000），
     * 靠 {@code styles.xml} 里挂的格式来决定显示成「45000」还是「2023-03-15」。
     * 不判断的话，生日列导进来会变成一串 45000。
     *
     * <p>单元格的 {@code s="5"} 指向的是 {@code <cellXfs>} 里的第 5 个 {@code <xf>}，
     * 不是 {@code <cellStyleXfs>} 里的 —— 这两个都叫 xf，取错了会全表判成日期或全判错。
     */
    private Set<Integer> parseDateStyles(byte[] xml) throws IOException {
        Set<Integer> dateStyles = new HashSet<>();
        Document doc = parseXml(xml);
        if (doc == null) {
            return dateStyles;
        }

        // 自定义格式：numFmtId(≥164) → formatCode，如 165 → "yyyy\"年\"m\"月\"d\"日\""
        Map<Integer, String> customCodes = new HashMap<>();
        NodeList fmts = doc.getElementsByTagNameNS("*", "numFmt");
        for (int i = 0; i < fmts.getLength(); i++) {
            Element fmt = (Element) fmts.item(i);
            customCodes.put(parseInt(fmt.getAttribute("numFmtId"), -1), fmt.getAttribute("formatCode"));
        }

        Element cellXfs = firstChild(doc.getDocumentElement(), "cellXfs");
        if (cellXfs == null) {
            return dateStyles;
        }
        NodeList xfs = cellXfs.getElementsByTagNameNS("*", "xf");
        for (int i = 0; i < xfs.getLength(); i++) {
            Element xf = (Element) xfs.item(i);
            int fmtId = parseInt(xf.getAttribute("numFmtId"), 0);
            if (isDateFormat(fmtId, customCodes.get(fmtId))) {
                dateStyles.add(i);
            }
        }
        return dateStyles;
    }

    /**
     * 这个数字格式是不是日期。
     *
     * <p>内置格式里 14~22 是各种日期，45~47 是分秒计时。
     * 自定义格式没有固定表，只能看格式串里有没有 y/m/d。
     * 判断前先把双引号里的字面量去掉 —— {@code 0"元"0"角"} 这种里面没有 ymd，
     * 但 {@code 0"天"} 会被误判，去掉引号内容才不会。
     */
    private boolean isDateFormat(int fmtId, String code) {
        if (fmtId >= 14 && fmtId <= 22) {
            return true;
        }
        if (fmtId >= 45 && fmtId <= 47) {
            return true;
        }
        if (code == null || code.isEmpty()) {
            return false;
        }
        String clean = code.replaceAll("\"[^\"]*\"", "").toLowerCase();
        return clean.contains("yy") || clean.contains("m月") || clean.contains("d日")
                || clean.contains("yyyy") || clean.contains("dd");
    }

    /* ================= 4. 定位第一个工作表 ================= */

    /** 第一个 sheet 的名字和它在 zip 里的路径 */
    private static final class SheetRef {
        final String name;
        final String path;

        SheetRef(String name, String path) {
            this.name = name;
            this.path = path;
        }
    }

    /**
     * workbook.xml 只写了 sheet 的名字和 rId，真实路径要去 rels 里查。
     *
     * <p>为什么不能直接猜 {@code xl/worksheets/sheet1.xml}：
     * 大多数情况下确实是这个名字，但用 WPS / 在线表格工具导出时可能变成 sheet2.xml
     * 或者带随机后缀。查一次 rels 只要几行，猜错却会报「找不到工作表」。
     */
    private SheetRef firstSheet(Map<String, byte[]> parts) throws IOException {
        String name = "Sheet1";
        String relId = null;

        Document wb = parseXml(parts.get("xl/workbook.xml"));
        if (wb != null) {
            NodeList sheets = wb.getElementsByTagNameNS("*", "sheet");
            if (sheets.getLength() > 0) {
                Element sheet = (Element) sheets.item(0);
                name = sheet.getAttribute("name");
                relId = relationshipId(sheet);
            }
        }

        String path = null;
        Document rels = parseXml(parts.get("xl/_rels/workbook.xml.rels"));
        if (rels != null && relId != null) {
            NodeList list = rels.getElementsByTagNameNS("*", "Relationship");
            for (int i = 0; i < list.getLength(); i++) {
                Element rel = (Element) list.item(i);
                if (!relId.equals(rel.getAttribute("Id"))) {
                    continue;
                }
                String target = rel.getAttribute("Target");
                if (target == null || target.isEmpty()) {
                    continue;
                }
                // Target 有两种写法："worksheets/sheet1.xml"（相对 xl/）和 "/xl/worksheets/sheet1.xml"（绝对）
                if (target.startsWith("/")) {
                    path = target.substring(1);
                } else if (target.startsWith("xl/")) {
                    path = target;
                } else {
                    path = "xl/" + target;
                }
                break;
            }
        }

        // 兜底：rels 缺失或格式怪异时按最常见的路径找，还找不到就让上层报错
        if (path == null || !parts.containsKey(path)) {
            path = "xl/worksheets/sheet1.xml";
        }
        return new SheetRef(name, path);
    }

    /**
     * 读 {@code r:id}。
     *
     * <p>{@code getAttribute("r:id")} 在 namespaceAware 模式下取不到 ——
     * 带前缀的属性必须用 {@code getAttributeNS}，或者遍历属性看 localName。
     * 这里两条路都走一遍，兼容不同工具导出的写法。
     */
    private String relationshipId(Element el) {
        String v = el.getAttributeNS(NS_REL, "id");
        if (v != null && !v.isEmpty()) {
            return v;
        }
        NamedNodeMap attrs = el.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Node a = attrs.item(i);
            if ("id".equals(a.getLocalName()) && a.getNamespaceURI() != null) {
                return a.getNodeValue();
            }
        }
        return null;
    }

    /* ================= 5. 读行列 ================= */

    private SheetData parseSheet(String name, byte[] xml, List<String> shared, Set<Integer> dateStyles)
            throws IOException {
        SheetData data = new SheetData(name);
        Document doc = parseXml(xml);
        if (doc == null) {
            return data;
        }
        Element sheetData = firstChild(doc.getDocumentElement(), "sheetData");
        if (sheetData == null) {
            return data;
        }

        NodeList rows = sheetData.getElementsByTagNameNS("*", "row");
        int blankRows = 0;
        int lastRowNo = 0;

        for (int i = 0; i < rows.getLength(); i++) {
            Element row = (Element) rows.item(i);

            // Excel 行号：r 属性是 1 开始的字符串。偶尔缺失（某些工具导出），就按上一行 +1 推
            int rowNo = parseInt(row.getAttribute("r"), lastRowNo + 1);
            if (rowNo <= lastRowNo) {
                rowNo = lastRowNo + 1;
            }
            lastRowNo = rowNo;

            List<String> cells = new ArrayList<>();
            NodeList cs = row.getElementsByTagNameNS("*", "c");
            int nextCol = 0;
            for (int j = 0; j < cs.getLength(); j++) {
                Element c = (Element) cs.item(j);

                // 单元格的 r 属性是「列字母+行号」，如 "C15"。
                // 空单元格 Excel 直接不写 <c>，所以必须靠 r 定位，不能按顺序 push
                int col = colIndex(c.getAttribute("r"));
                if (col < 0) {
                    col = nextCol;
                }
                nextCol = col + 1;

                while (cells.size() <= col) {
                    cells.add(null);
                }
                cells.set(col, cellValue(c, shared, dateStyles));
            }

            boolean blank = true;
            for (String v : cells) {
                if (v != null && !v.isBlank()) {
                    blank = false;
                    break;
                }
            }
            if (blank) {
                blankRows++;
            }
            // 空行也加进去：保留它是为了让数组下标和 Excel 行号一一对应
            data.addRow(rowNo, cells);
        }

        data.setBlankRows(blankRows);
        return data;
    }

    /**
     * 取一个单元格的文本值。
     *
     * <p>按 {@code t} 属性分派：
     * <ul>
     *   <li>{@code s} → 共享字符串下标</li>
     *   <li>{@code inlineStr} → 直接内嵌文本</li>
     *   <li>{@code str} → 公式算出来的字符串</li>
     *   <li>{@code b} → 布尔</li>
     *   <li>{@code e} → 错误值（#N/A 之类），当空处理</li>
     *   <li>没有 t / {@code n} → 数字，还要看样式判断是不是日期</li>
     * </ul>
     */
    private String cellValue(Element c, List<String> shared, Set<Integer> dateStyles) {
        String type = c.getAttribute("t");

        if ("inlineStr".equals(type)) {
            Element is = firstChild(c, "is");
            return is == null ? null : emptyToNull(richText(is));
        }

        Element v = firstChild(c, "v");
        if (v == null) {
            return null;
        }
        String raw = v.getTextContent();
        if (raw == null) {
            return null;
        }
        raw = raw.trim();
        if (raw.isEmpty()) {
            return null;
        }

        if ("s".equals(type)) {
            int idx = parseInt(raw, -1);
            return idx >= 0 && idx < shared.size() ? emptyToNull(shared.get(idx)) : null;
        }
        if ("str".equals(type)) {
            return raw;
        }
        if ("b".equals(type)) {
            return "1".equals(raw) ? "TRUE" : "FALSE";
        }
        if ("e".equals(type)) {
            return null;
        }

        int style = parseInt(c.getAttribute("s"), -1);
        if (dateStyles.contains(style)) {
            String date = excelDate(raw);
            if (date != null) {
                return date;
            }
        }
        return plainNumber(raw);
    }

    /**
     * 数字转字符串，<b>这是「学号统一按字符串处理」最关键的一步</b>。
     *
     * <p>Excel 里学号如果是数字格式，{@code <v>} 里可能是
     * {@code 20280001}、{@code 20280001.0}、甚至 {@code 2.0280001E7} 三种写法，
     * 直接 toString 会得到 "2.0280001E7" 这种没法用的东西。
     *
     * <p>用 BigDecimal 而不是 Double：Double 处理 18 位学号会丢精度
     * （9007199254740993 会变成 ...992），BigDecimal 不会。
     */
    private String plainNumber(String raw) {
        try {
            BigDecimal bd = new BigDecimal(raw);
            // stripTrailingZeros 把 20280001.0 变成 2.0280001E+7，
            // toPlainString 再把它变回 "20280001"（不会输出科学计数法）。
            // 这两步合起来就是「数字长得像整数就输出整数」，
            // 而 95.5 这种小数会原样保留
            return bd.stripTrailingZeros().toPlainString();
        } catch (NumberFormatException e) {
            // 不是数字（有些工具会写 t="n" 但内容是文本），原样返回
            return raw;
        }
    }

    /**
     * Excel 日期序列号 → {@code yyyy-MM-dd}。
     *
     * <p>范围之外（比如 0 或 40000 多）返回 null，交给上层当普通数字处理，
     * 免得把「总分 45000」错认成日期。
     */
    private String excelDate(String raw) {
        try {
            double d = Double.parseDouble(raw);
            if (d < 1 || d > 2958465) {
                return null;
            }
            return EXCEL_EPOCH.plusDays((long) Math.floor(d)).toString();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * "AB12" → 27（0 开始）。
     *
     * <p>就是 26 进制，但没有 0：A=1, Z=26, AA=27。
     */
    private int colIndex(String ref) {
        if (ref == null || ref.isEmpty()) {
            return -1;
        }
        int n = 0;
        int i = 0;
        while (i < ref.length() && Character.isLetter(ref.charAt(i))) {
            n = n * 26 + (Character.toUpperCase(ref.charAt(i)) - 'A' + 1);
            i++;
        }
        return i == 0 ? -1 : n - 1;
    }

    /* ================= 6. XML 工具 ================= */

    /**
     * 解析 XML，<b>关掉一切外部实体</b>。
     *
     * <p>这是必须的：上传的 xlsx 是不可信输入，默认配置下攻击者可以构造一个
     * 引用 {@code file:///C:/...} 的 XML 把服务器上的文件内容读出来（XXE）。
     * 我们只解析自己需要的几个部件，关掉 DTD 和外部实体没有任何副作用。
     */
    private Document parseXml(byte[] data) throws IOException {
        if (data == null || data.length == 0) {
            return null;
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            // 不给 handler 的话，格式错误会直接打到 stderr，日志里一片红
            builder.setErrorHandler(new ErrorHandler() {
                @Override
                public void warning(SAXParseException e) {
                }

                @Override
                public void error(SAXParseException e) {
                }

                @Override
                public void fatalError(SAXParseException e) throws SAXParseException {
                    throw e;
                }
            });
            return builder.parse(new ByteArrayInputStream(data));
        } catch (Exception e) {
            throw new IOException("表格内容解析失败，文件可能已损坏：" + e.getMessage(), e);
        }
    }

    /** 只找直接子元素，不用 getElementsByTagName（那个会连孙子一起找） */
    private Element firstChild(Element parent, String localName) {
        if (parent == null) {
            return null;
        }
        NodeList kids = parent.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            Node n = kids.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE && localName.equals(n.getLocalName())) {
                return (Element) n;
            }
        }
        return null;
    }

    private int parseInt(String s, int def) {
        if (s == null || s.isEmpty()) {
            return def;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }
}
