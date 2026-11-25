package com.banxing.classassistant.importer;

import java.io.IOException;
import java.io.InputStream;

/**
 * 表格文件解析器。一个实现类负责一种文件格式。
 *
 * <p><b>这就是「预留 XLS / CSV 解析接口」落地的地方。</b>
 * 加一种新格式要做的事只有两件：
 * <ol>
 *   <li>新建 {@code XlsWorkbookParser implements WorkbookParser}，类上加 {@code @Component}</li>
 *   <li>在 {@link ParserRegistry} 里什么也不用改 —— 它是按 Spring 注入的列表自动登记的</li>
 * </ol>
 *
 * <p>上层（{@code StudentImportServiceImpl}）只依赖这个接口，
 * 永远不知道拿到的是 xlsx 还是 csv，所以加格式不会碰到任何业务代码。
 *
 * <p>第二期的两个实现大概是这个量级：
 * <ul>
 *   <li>CSV：约 80 行（要处理 BOM、引号转义、GBK/UTF-8 编码探测）</li>
 *   <li>XLS：约 40 行 + 一个 Apache POI 依赖（老的二进制格式没法手写，
 *       必须用 poi 的 HSSF；这是唯一一个绕不开第三方库的格式）</li>
 * </ul>
 */
public interface WorkbookParser {

    /**
     * 这个解析器负责的扩展名，小写不带点。如 {@code "xlsx"}。
     *
     * <p>用它而不是 MIME 类型：微信小程序 {@code chooseMessageFile} 给的
     * 文件对象里只有 name 和 path，没有可靠的 MIME。
     */
    String extension();

    /** 给人看的格式名，用在「暂不支持 .xls，请另存为 .xlsx」这类提示里 */
    String displayName();

    /**
     * 解析第一个工作表。
     *
     * @param in 文件流。实现类不要 close 它 —— 由调用方（try-with-resources）负责
     * @return 纯文本矩阵，空格子为 null
     * @throws IOException 文件损坏、不是合法 zip、缺 sheet1.xml 等
     */
    SheetData parse(InputStream in) throws IOException;
}
