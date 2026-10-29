package com.banxing.classassistant.controller;

import com.banxing.classassistant.common.Result;
import com.banxing.classassistant.dto.StudentDetailVO;
import com.banxing.classassistant.dto.StudentListVO;
import com.banxing.classassistant.dto.StudentSaveDTO;
import com.banxing.classassistant.service.StudentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 学生接口
 */
@RestController
@RequestMapping("/api/teacher/student")
@RequiredArgsConstructor
public class StudentController {

    private final StudentService studentService;

    /**
     * 学生列表
     * GET /api/teacher/student/list?keyword=王&status=1
     *
     * keyword 和 status 都是可选的，所以 required = false。
     * 不加这个，前端不传参数时会直接报 400
     */
    @GetMapping("/list")
    public Result<StudentListVO> list(
            HttpServletRequest request,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {

        // 拦截器解析 token 后塞进来的，这里直接取
        Long userId = (Long) request.getAttribute("userId");

        return Result.success(studentService.list(userId, keyword, status));
    }

    /**
     * 学生详情
     * GET /api/teacher/student/{id}
     *
     * 路径变量和 /list 不会打架：Spring 先匹配字面量 /list，再匹配 {id}。
     * 但 id 必须是数字，否则 /list 会被当成 id="list" 传给 Long 报 400 ——
     * 现在没有这种冲突的路径，将来加 /export 之类要注意
     */
    @GetMapping("/{id}")
    public Result<StudentDetailVO> detail(
            HttpServletRequest request,
            @PathVariable Long id) {

        Long userId = (Long) request.getAttribute("userId");

        return Result.success(studentService.detail(userId, id));
    }

    /**
     * 新增学生
     * POST /api/teacher/student
     *
     * {@code @Valid} 会触发 StudentSaveDTO 上的注解校验，
     * 不通过时抛 MethodArgumentNotValidException，
     * 由 GlobalExceptionHandler 转成 1001 + 具体是哪条不合法。
     *
     * 返回新学生的 id，前端拿到后可以直接跳详情页
     */
    @PostMapping
    public Result<Map<String, Long>> create(
            HttpServletRequest request,
            @Valid @RequestBody StudentSaveDTO dto) {

        Long userId = (Long) request.getAttribute("userId");
        Long id = studentService.create(userId, dto);

        return Result.success(Map.of("id", id));
    }

    /**
     * 编辑学生
     * PUT /api/teacher/student/{id}
     *
     * 全量提交：前端把整个对象传回来，没传的字段会被清空。
     * 用 PUT 而不是 PATCH 是因为 wx.request 不支持 PATCH（见契约 §0）。
     */
    @PutMapping("/{id}")
    public Result<Void> update(
            HttpServletRequest request,
            @PathVariable Long id,
            @Valid @RequestBody StudentSaveDTO dto) {

        Long userId = (Long) request.getAttribute("userId");
        studentService.update(userId, id, dto);

        return Result.success(null);
    }

    /**
     * 移出班级（软删除）
     * DELETE /api/teacher/student/{id}
     *
     * 实际是把 status 改成 0，数据还在。
     * 幂等：已经离班的再调一次也返回成功。
     */
    @DeleteMapping("/{id}")
    public Result<Void> remove(
            HttpServletRequest request,
            @PathVariable Long id) {

        Long userId = (Long) request.getAttribute("userId");
        studentService.remove(userId, id);

        return Result.success(null);
    }
}