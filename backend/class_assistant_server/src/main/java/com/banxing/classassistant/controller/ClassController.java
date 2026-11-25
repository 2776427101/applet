package com.banxing.classassistant.controller;

import com.banxing.classassistant.common.Result;
import com.banxing.classassistant.dto.ClassSaveDTO;
import com.banxing.classassistant.dto.ClassVO;
import com.banxing.classassistant.service.ClassService;
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

import java.util.List;
import java.util.Map;

/**
 * 班级接口。
 *
 * <p><b>v0.5：一个老师可以带多个班。</b> 所有接口都多了一个可选的 {@code classId}：
 * <ul>
 *   <li>传了 → 必须是自己的班，否则 3002</li>
 *   <li>不传 → 用"我的第一个班"，只为了让还没改造的页面不至于报错</li>
 * </ul>
 * 班级 id 仍然不收进路径（{@code /class/{id}}），而是走 query：
 * 路径里的 id 会被当成"资源主键"，而这里的 classId 本质是"在哪个班上下文中操作"。
 *
 * <p>新增了 {@code GET /list} 和 {@code POST /class}：
 * 多班级的前提是老师能看见自己有哪些班、能再建一个。
 */
@RestController
@RequestMapping("/api/teacher/class")
@RequiredArgsConstructor
public class ClassController {

    private final ClassService classService;

    /**
     * 我的班级列表（首页选择器 + 通讯录都用这一个）
     * GET /api/teacher/class/list
     *
     * <p>每个班带上在读人数和班级群 id —— 选择器要人数，通讯录要群入口。
     */
    @GetMapping("/list")
    public Result<List<ClassVO>> list(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(classService.list(userId));
    }

    /**
     * 班级设置页的初始数据
     * GET /api/teacher/class?classId=2
     */
    @GetMapping
    public Result<ClassVO> detail(
            HttpServletRequest request,
            @RequestParam(required = false) Long classId) {

        Long userId = (Long) request.getAttribute("userId");
        return Result.success(classService.detail(userId, classId));
    }

    /**
     * 新建班级
     * POST /api/teacher/class
     * body: { "name":"24级软通1班", "grade":"大一", "termStartDate":"2026-09-01" }
     *
     * <p>建班时会自动建好这个班的班级群（班主任为群主），
     * 所以返回的 id 之外不用前端再调一次建群接口。
     */
    @PostMapping
    public Result<Map<String, Long>> create(
            HttpServletRequest request,
            @Valid @RequestBody ClassSaveDTO dto) {

        Long userId = (Long) request.getAttribute("userId");
        Long id = classService.create(userId, dto);
        return Result.success(Map.of("id", id));
    }

    /**
     * 给班级补建班级群（幂等）
     * POST /api/teacher/class/{classId}/group
     *
     * 正常流程用不到：建班时已经建好了。给历史数据和"建群失败"兜底。
     */
    @PostMapping("/{classId}/group")
    public Result<Map<String, Long>> ensureGroup(
            HttpServletRequest request,
            @PathVariable Long classId) {

        Long userId = (Long) request.getAttribute("userId");
        Long containerId = classService.ensureClassGroup(userId, classId);
        return Result.success(Map.of("containerId", containerId));
    }

    /**
     * 保存班级设置
     * PUT /api/teacher/class?classId=2
     *
     * 只认 name / grade / termStartDate 三个字段。
     * 用 PUT 不用 PATCH 是因为 wx.request 不支持 PATCH（见契约 §0）。
     */
    @PutMapping
    public Result<Void> update(
            HttpServletRequest request,
            @RequestParam(required = false) Long classId,
            @Valid @RequestBody ClassSaveDTO dto) {

        Long userId = (Long) request.getAttribute("userId");
        classService.update(userId, classId, dto);

        return Result.success(null);
    }

    /**
     * 删除班级
     * DELETE /api/teacher/class/2
     *
     * <p><b>这里 classId 走路径，和其它接口不一样。</b>
     * 别的接口里 classId 是"在哪个班上下文中操作"，所以放 query；
     * 而这个接口删除的<b>就是班级本身</b>，它是资源主键，放路径才对。
     *
     * <p>实际是归档（status=0），不是物理删除：这个班名下的学生、成绩、
     * 座位、请假、聊天记录都还在，只是不再出现在任何列表里。
     */
    @DeleteMapping("/{classId}")
    public Result<Void> delete(
            HttpServletRequest request,
            @PathVariable Long classId) {

        Long userId = (Long) request.getAttribute("userId");
        classService.delete(userId, classId);

        return Result.success(null);
    }
}
