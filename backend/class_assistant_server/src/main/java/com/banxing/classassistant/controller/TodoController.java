package com.banxing.classassistant.controller;

import com.banxing.classassistant.common.Result;
import com.banxing.classassistant.dto.TodoCreateDTO;
import com.banxing.classassistant.dto.TodoDoneDTO;
import com.banxing.classassistant.dto.TodoListVO;
import com.banxing.classassistant.service.TodoService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 待办接口
 * 4 个方法，每个第一件事都是取 userId，然后原样往下传
 */
@RestController
@RequestMapping("/api/teacher/todo")
@RequiredArgsConstructor
public class TodoController {

    private final TodoService todoService;

    /**
     * 列表
     * GET /api/teacher/todo/list?done=0
     */
    @GetMapping("/list")
    public Result<TodoListVO> list(
            HttpServletRequest request,
            @RequestParam(required = false) Integer done) {

        Long userId = (Long) request.getAttribute("userId");
        return Result.success(todoService.list(userId, done));
    }

    /**
     * 新增
     * POST /api/teacher/todo
     * body: { "title": "收齐家长回执", "dueAt": "2026-11-01 18:00:00" }
     */
    @PostMapping
    public Result<Map<String, Long>> add(
            HttpServletRequest request,
            @RequestBody TodoCreateDTO dto) {

        Long userId = (Long) request.getAttribute("userId");
        Long id = todoService.add(userId, dto);

        // 只有一个字段，不值得为它单独建一个 VO 类。
        // 用 Map.of 直接拼，Jackson 会输出 {"id": 3}
        return Result.success(Map.of("id", id));
    }

    /**
     * 完成 / 取消完成
     * PUT /api/teacher/todo/3/done
     * body: { "done": 1 }
     */
    @PutMapping("/{id}/done")
    public Result<Void> setDone(
            HttpServletRequest request,
            @PathVariable Long id,
            @RequestBody TodoDoneDTO dto) {

        Long userId = (Long) request.getAttribute("userId");
        todoService.setDone(userId, id, dto.getDone());
        return Result.success(null);
    }

    /**
     * 删除
     * DELETE /api/teacher/todo/3
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(
            HttpServletRequest request,
            @PathVariable Long id) {

        Long userId = (Long) request.getAttribute("userId");
        todoService.delete(userId, id);
        return Result.success(null);
    }
}