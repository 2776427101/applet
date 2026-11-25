package com.banxing.classassistant.importer;

import com.banxing.classassistant.common.BizException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 按扩展名挑解析器。
 *
 * <p><b>这就是「预留接口」的兑现方式</b>：构造器收的是 {@code List<WorkbookParser>}，
 * Spring 会把所有带 {@code @Component} 的实现全塞进来。
 * 第二期加 CSV 支持时，只需要新建 {@code CsvWorkbookParser} 并标上 {@code @Component}，
 * <b>这个文件一行都不用改</b>，也不用手动往 map 里注册。
 *
 * <p>所以「支持 XLSX，预留 XLS/CSV」不是一句空话 ——
 * 加格式的成本已经被压到「写一个类」，业务代码完全不用碰。
 */
@Component
public class ParserRegistry {

    private final Map<String, WorkbookParser> byExtension;

    public ParserRegistry(List<WorkbookParser> parsers) {
        Map<String, WorkbookParser> map = new LinkedHashMap<>();
        for (WorkbookParser p : parsers) {
            map.put(p.extension().toLowerCase(Locale.ROOT), p);
        }
        this.byExtension = map;
    }

    /**
     * 按文件名挑解析器。
     *
     * <p>只用后缀做初步筛选，<b>真正的格式判断交给解析器自己</b>：
     * 把 .xls 改名成 .xlsx 也能过这一关，但 XlsxWorkbookParser 解不开 zip 时会报
     * 「这不是有效的 xlsx 文件」，比在这里猜靠谱。
     *
     * @param filename 原始文件名，如「2028届软通班学生名单.xlsx」
     */
    public WorkbookParser require(String filename) {
        String ext = extensionOf(filename);
        if (ext.isEmpty()) {
            throw new BizException(1001, "文件没有扩展名，无法判断格式");
        }
        WorkbookParser parser = byExtension.get(ext);
        if (parser != null) {
            return parser;
        }

        // 错误信息要能直接指导老师下一步做什么，不能只说「不支持」
        if ("xls".equals(ext)) {
            throw new BizException(1001, "暂不支持老版 .xls，请在 Excel 里「另存为」.xlsx 后再上传");
        }
        if ("csv".equals(ext)) {
            throw new BizException(1001, "暂不支持 .csv，请在 Excel 里「另存为」.xlsx 后再上传");
        }
        throw new BizException(1001, "不支持的文件格式 ." + ext + "，目前只支持 " + supportedText());
    }

    /** 「.xlsx」这种给人看的列表，用在提示里 */
    public String supportedText() {
        return byExtension.keySet().stream()
                .map(e -> "." + e)
                .collect(Collectors.joining("、"));
    }

    /** 小写扩展名，没有就返回空串 */
    public static String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
