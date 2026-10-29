package com.banxing.classassistant.controller;

import com.banxing.classassistant.common.Result;
import com.banxing.classassistant.dto.ScoreImportCommitDTO;
import com.banxing.classassistant.dto.ScoreImportPreviewVO;
import com.banxing.classassistant.dto.ScoreImportResultVO;
import com.banxing.classassistant.service.ScoreImportService;
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
 * 成绩表导入接口。
 *
 * <p>单独一个 Controller 而不是塞进 {@link ScoreController}：
 * 和名单导入一样，这两个接口收的是文件和另一套 DTO。
 * 路径前缀 {@code /score/import} 和 {@code ScoreController} 的
 * {@code /list}、{@code /batch} 不冲突。
 */
@RestController
@RequestMapping("/api/teacher/score/import")
@RequiredArgsConstructor
public class ScoreImportController {

    private final ScoreImportService importService;

    /**
     * 上传成绩表并预览，<b>不写库</b>。
     *
     * <p>{@code multipart/form-data}，字段名固定为 {@code file}，
     * 另外必须带一个普通表单字段 {@code examId} ——
     * 成绩要落到哪场考试是必填的，而且不能从 token 推出来。
     *
     * <p>{@code file} 用 {@code required = false}：不传时让 Service 报
     * 「请选择要导入的文件」，而不是掉进兜底变成 5000「服务器开小差了」。
     */
    @PostMapping("/preview")
    public Result<ScoreImportPreviewVO> preview(
            HttpServletRequest request,
            @RequestParam(value = "examId", required = false) Long examId,
            @RequestParam(value = "file", required = false) MultipartFile file) {

        Long userId = (Long) request.getAttribute("userId");
        return Result.success(importService.preview(userId, examId, file));
    }

    /**
     * 确认导入。
     *
     * <p>请求体里的 rows 就是预览返回的那一份，原样回传。
     * 后端会重新校验分数范围和科目 —— 前端传的 action / 统计一概不看。
     */
    @PostMapping("/commit")
    public Result<ScoreImportResultVO> commit(
            HttpServletRequest request,
            @Valid @RequestBody ScoreImportCommitDTO dto) {

        Long userId = (Long) request.getAttribute("userId");
        return Result.success(importService.commit(userId, dto));
    }
}
