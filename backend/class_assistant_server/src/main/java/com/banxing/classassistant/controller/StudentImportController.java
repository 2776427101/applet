package com.banxing.classassistant.controller;

import com.banxing.classassistant.common.Result;
import com.banxing.classassistant.dto.ImportCommitDTO;
import com.banxing.classassistant.dto.ImportPreviewVO;
import com.banxing.classassistant.dto.ImportResultVO;
import com.banxing.classassistant.service.StudentImportService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 学生名单导入接口。
 *
 * <p>单独一个 Controller 而不是塞进 {@code StudentController}：
 * 这两个接口收的是文件和另一套 DTO，混在一起会让「学生增删改查」那个类变得很长。
 * 路径前缀 {@code /student/import} 和 {@code StudentController} 的
 * {@code /{id}} 不冲突 —— 前者是两段路径，后者只匹配一段。
 */
@RestController
@RequestMapping("/api/teacher/student/import")
@RequiredArgsConstructor
public class StudentImportController {

    private final StudentImportService importService;

    /**
     * 上传文件并预览，<b>不写库</b>。
     *
     * <p>{@code multipart/form-data}，字段名固定为 {@code file}。
     * 用 {@code @RequestParam("file")} 而不是 {@code @RequestPart}：
     * 小程序 {@code uni.uploadFile} 传的是普通表单字段，不是 part 里的 JSON。
     *
     * <p>{@code required = false} 是故意的：不传时 Spring 会抛
     * {@code MissingServletRequestPartException}，那个异常没被全局处理器接住，
     * 会掉进兜底变成 5000「服务器开小差了」。改成可选之后由 Service 判断，
     * 就能返回「请选择要导入的文件」这种能指导用户的话。
     */
    @PostMapping("/preview")
    public Result<ImportPreviewVO> preview(
            HttpServletRequest request,
            @RequestParam(value = "file", required = false) MultipartFile file) {

        Long userId = (Long) request.getAttribute("userId");
        return Result.success(importService.preview(userId, file));
    }

    /**
     * 确认导入。
     *
     * <p>请求体里的 rows 就是预览返回的那一份，原样回传即可。
     * 后端会重新校验 —— 前端传的 action 不作数。
     */
    @PostMapping("/commit")
    public Result<ImportResultVO> commit(
            HttpServletRequest request,
            @Valid @RequestBody ImportCommitDTO dto) {

        Long userId = (Long) request.getAttribute("userId");
        return Result.success(importService.commit(userId, dto));
    }
}
