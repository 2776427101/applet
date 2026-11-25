package com.banxing.classassistant.service.impl;

import com.banxing.classassistant.common.BizException;
import com.banxing.classassistant.dto.UploadVO;
import com.banxing.classassistant.service.ContainerAuthService;
import com.banxing.classassistant.service.ContainerFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 群聊附件落盘实现。
 *
 * <p>三条安全底线：
 * <ol>
 *   <li><b>文件名不用用户给的</b>：存成 UUID + 白名单扩展名。
 *       用原始名会带来 {@code ../} 穿越和 {@code .jsp} 这类可执行后缀的问题</li>
 *   <li><b>扩展名白名单按 kind 分开</b>：图片接口不能传 .exe 再改成 .jpg 骗过去 ——
 *       校验的是最终落盘的扩展名</li>
 *   <li><b>先校验成员身份再落盘</b>：非成员不该能往群里塞文件</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class ContainerFileServiceImpl implements ContainerFileService {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");

    private static final Set<String> IMAGE_EXT = Set.of("jpg", "jpeg", "png", "gif", "webp", "bmp");
    private static final Set<String> VIDEO_EXT = Set.of("mp4", "mov", "m4v", "3gp");
    /** 文档类。刻意不含 exe/bat/sh/js/html —— 群里发可执行文件是最常见的钓鱼手法 */
    private static final Set<String> FILE_EXT = Set.of(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "txt", "md", "csv", "zip", "rar", "7z");

    private final ContainerAuthService auth;

    @Value("${app.upload.dir}")
    private String uploadDir;

    @Value("${app.upload.max-image-mb:10}")
    private long maxImageMb;

    @Value("${app.upload.max-video-mb:30}")
    private long maxVideoMb;

    @Value("${app.upload.max-file-mb:20}")
    private long maxFileMb;

    @Override
    public UploadVO upload(Long userId, Long containerId, MultipartFile file, String kind) {
        // 第一步永远是权限：非成员连"传个文件试试"都不该发生
        auth.requireMember(userId, containerId);
        return store(containerId, file, kind);
    }

    @Override
    public UploadVO uploadForStudent(Long containerId, MultipartFile file, String kind) {
        /* 这里刻意没有再判一次权限：学生不在 t_container_member 里，
           用 requireMember 判等于把学生全部拒掉。调用方先判"这个群我看不看得见"，
           判完才进来 —— 权限判断只有一个入口，不在这里做第二次也不漏 */
        return store(containerId, file, kind);
    }

    /** 真正的落盘：校验类型、大小、扩展名，写文件，返回相对路径 */
    private UploadVO store(Long containerId, MultipartFile file, String kind) {
        if (file == null || file.isEmpty()) {
            throw new BizException(1001, "没有选到文件");
        }

        String k = StringUtils.hasText(kind) ? kind.trim().toLowerCase(Locale.ROOT) : "image";
        Set<String> allowed;
        long maxBytes;
        switch (k) {
            case "image" -> {
                allowed = IMAGE_EXT;
                maxBytes = maxImageMb * 1024 * 1024;
            }
            case "video" -> {
                allowed = VIDEO_EXT;
                maxBytes = maxVideoMb * 1024 * 1024;
            }
            case "file" -> {
                allowed = FILE_EXT;
                maxBytes = maxFileMb * 1024 * 1024;
            }
            default -> throw new BizException(1001, "不支持的文件类型");
        }

        String original = file.getOriginalFilename();
        String ext = extensionOf(original);
        if (!StringUtils.hasText(ext) || !allowed.contains(ext)) {
            throw new BizException(1001, "不支持这种格式，请换一个文件");
        }
        if (file.getSize() > maxBytes) {
            throw new BizException(1001, "文件太大，单个不超过 " + (maxBytes / 1024 / 1024) + "MB");
        }

        // 落盘名一律用 uuid：原始名只用来取扩展名和展示
        String stored = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        String month = LocalDate.now().format(MONTH);
        // 目录层级按 群组/月份 分开，单目录几十万文件时 ls 和备份都会很难受
        String relative = "container/" + containerId + "/" + month + "/" + stored;

        Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path target = root.resolve(relative).normalize();
        // 双保险：解析后的路径必须仍在根目录内（stored 是我们自己生成的，理论上不会越界）
        if (!target.startsWith(root)) {
            throw new BizException(1001, "非法路径");
        }

        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (IOException e) {
            throw new BizException(5000, "文件保存失败，请重试");
        }

        UploadVO vo = new UploadVO();
        // 返回给前端的是相对路径，域名由前端拼 —— 换域名不用改库
        vo.setUrl("/files/" + relative);
        vo.setName(StringUtils.hasText(original) ? original : stored);
        vo.setSize(file.getSize());
        return vo;
    }

    /** 取小写扩展名；没有扩展名返回 null */
    private String extensionOf(String name) {
        if (!StringUtils.hasText(name)) {
            return null;
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return null;
        }
        String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        // 扩展名里再出现路径分隔符就一定是有人在做坏事
        if (ext.contains("/") || ext.contains("\\") || ext.length() > 10) {
            return null;
        }
        return ext;
    }
}
