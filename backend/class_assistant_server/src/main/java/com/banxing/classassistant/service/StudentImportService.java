package com.banxing.classassistant.service;

import com.banxing.classassistant.dto.ImportCommitDTO;
import com.banxing.classassistant.dto.ImportPreviewVO;
import com.banxing.classassistant.dto.ImportResultVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 学生名单导入。
 *
 * <p>为什么拆成「预览」和「提交」两个接口，而不是一次上传直接写完：
 * <ol>
 *   <li>老师得先看见「表头认对了吗」「有 3 行学号重复」才能决定要不要导</li>
 *   <li>「学号已存在的怎么处理」这个选择，必须在写库之前做</li>
 * </ol>
 *
 * <p>两次请求之间不存服务端状态 —— 预览结果由前端回传。
 * 存 session 或缓存会让多实例部署失效，还要处理过期，
 * 而 69 行数据的 JSON 才 30KB，传一次比管缓存便宜。
 */
public interface StudentImportService {

    /**
     * 解析文件并预览，<b>不写库</b>。
     *
     * @param userId 当前登录老师
     * @param file   上传的 xlsx
     */
    ImportPreviewVO preview(Long userId, MultipartFile file);

    /**
     * 确认导入。
     *
     * <p>会重新校验每一行（不信任前端回传的 action），
     * 单行失败不影响其他行 —— 导入是「尽量多成」，不是「全成或全不成」。
     */
    ImportResultVO commit(Long userId, ImportCommitDTO dto);
}
