package com.banxing.classassistant.controller;

import com.banxing.classassistant.common.Result;
import com.banxing.classassistant.dto.ClassSaveDTO;
import com.banxing.classassistant.dto.ClassVO;
import com.banxing.classassistant.service.ClassService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 班级设置接口。
 *
 * <p>只有两个：读和改。没有「新建班级」和「删除班级」——
 * 班级是老师注册时由后台建的，一个老师带一个班，
 * 前端能做的只是把名字、年级、开学日期改对。
 *
 * <p>路径用 {@code /api/teacher/class}（单数），不带 {id}。
 * 这是刻意的：班级 id 只能从 token 里的 userId 反查出来，
 * 一旦允许前端传 id，就得在每个方法里校验「这个班是不是你的」。
 * 不收这个参数，越权就不可能发生。
 */
@RestController
@RequestMapping("/api/teacher/class")
@RequiredArgsConstructor
public class ClassController {

    private final ClassService classService;

    /**
     * 班级设置页的初始数据
     * GET /api/teacher/class
     */
    @GetMapping
    public Result<ClassVO> detail(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(classService.detail(userId));
    }

    /**
     * 保存班级设置
     * PUT /api/teacher/class
     *
     * 全量提交，只认 name / grade / termStartDate 三个字段。
     * 用 PUT 不用 PATCH 是因为 wx.request 不支持 PATCH（见契约 §0）。
     */
    @PutMapping
    public Result<Void> update(
            HttpServletRequest request,
            @Valid @RequestBody ClassSaveDTO dto) {

        Long userId = (Long) request.getAttribute("userId");
        classService.update(userId, dto);

        return Result.success(null);
    }
}
