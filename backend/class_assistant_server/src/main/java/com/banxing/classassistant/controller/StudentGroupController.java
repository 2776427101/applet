package com.banxing.classassistant.controller;

import com.banxing.classassistant.common.Result;
import com.banxing.classassistant.dto.StudentGroupJoinDTO;
import com.banxing.classassistant.dto.StudentGroupJoinVO;
import com.banxing.classassistant.dto.StudentGroupSendDTO;
import com.banxing.classassistant.dto.StudentGroupVO;
import com.banxing.classassistant.dto.StudentMessageVO;
import com.banxing.classassistant.dto.UploadVO;
import com.banxing.classassistant.service.StudentGroupService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 学生端的群聊
 *
 * <p>挂在 {@code /api/student/group} 下，所以 AuthInterceptor 会自动要求 role=2；
 * classId / studentId 全从 request 属性取，接口本身一个都不收。
 *
 * <p><b>这里没有"建群"，也没有"群主"</b>：群是老师端建的（`/api/teacher/container`），
 * 学生只是能看到自己该看的那些。学生能做的只有三件事 —— 看列表、读消息、发消息。
 *
 * <p>路径里带 {@code {id}} 是 {@code t_subject_container.id}。原来这里是单数、
 * 不带 id（"一个班只有一个群"），现在必须带：一个班可以有好几个群（班级群、课程群），
 * 而且学生还能用邀请码加入别的群。
 */
@RestController
@RequestMapping("/api/student/group")
@RequiredArgsConstructor
public class StudentGroupController {

    private final StudentGroupService groupService;

    /**
     * 我能看到的群
     * GET /api/student/group/list
     *
     * <p>本班的群排在前面（自动可见），用邀请码加入的排在后面。
     */
    @GetMapping("/list")
    public Result<List<StudentGroupVO>> list(HttpServletRequest request) {
        return Result.success(groupService.list(classId(request), studentId(request)));
    }

    /**
     * 单个群的信息（聊天页顶部：群名 + 人数）
     * GET /api/student/group/{id}
     */
    @GetMapping("/{id}")
    public Result<StudentGroupVO> info(HttpServletRequest request, @PathVariable Long id) {
        return Result.success(groupService.info(classId(request), studentId(request), id));
    }

    /**
     * 消息列表
     * GET /api/student/group/{id}/messages
     *
     * @param afterId 只拿比它新的（轮询用）。不传 = 最近 N 条
     * @param size    条数，默认 30，上限 50
     */
    @GetMapping("/{id}/messages")
    public Result<List<StudentMessageVO>> messages(HttpServletRequest request,
                                                   @PathVariable Long id,
                                                   @RequestParam(required = false) Long afterId,
                                                   @RequestParam(required = false) Integer size) {
        return Result.success(groupService.messages(
                classId(request), studentId(request), id, afterId, size));
    }

    /**
     * 发一条
     * POST /api/student/group/{id}/messages
     * body: { "content": "..." }
     */
    @PostMapping("/{id}/messages")
    public Result<StudentMessageVO> send(HttpServletRequest request,
                                         @PathVariable Long id,
                                         @Valid @RequestBody StudentGroupSendDTO dto) {
        return Result.success(groupService.send(classId(request), studentId(request), id, dto));
    }

    /**
     * 用邀请码加入别的群
     * POST /api/student/group/join
     * body: { "inviteCode": "953738D26C" }
     *
     * <p>本班的群本来就看得见，这里会返回 {@code alreadyIn=true}，不报错。
     */
    @PostMapping("/join")
    public Result<StudentGroupJoinVO> join(HttpServletRequest request,
                                           @Valid @RequestBody StudentGroupJoinDTO dto) {
        return Result.success(groupService.joinByCode(
                classId(request), studentId(request), dto.getInviteCode()));
    }

    /**
     * 传附件（图片 / 视频 / 文件）
     * POST /api/student/group/{id}/upload?kind=image
     *
     * <p>和老师端 {@code /api/container/{id}/upload} 是同一套落盘逻辑，
     * 只是权限判断换成"这个群我看不看得见" —— 学生不在成员表里。
     */
    @PostMapping("/{id}/upload")
    public Result<UploadVO> upload(HttpServletRequest request,
                                   @PathVariable Long id,
                                   @RequestParam(required = false, defaultValue = "image") String kind,
                                   @RequestPart("file") MultipartFile file) {
        return Result.success(groupService.upload(
                classId(request), studentId(request), id, file, kind));
    }

    /**
     * 退出用邀请码加入的群
     * POST /api/student/group/{id}/leave
     *
     * <p>本班的群会返回 1001「退不了」—— 那是班级的一部分，退不掉。
     */
    @PostMapping("/{id}/leave")
    public Result<Void> leave(HttpServletRequest request, @PathVariable Long id) {
        groupService.leave(classId(request), studentId(request), id);
        return Result.success(null);
    }

    private Long classId(HttpServletRequest request) {
        return (Long) request.getAttribute("classId");
    }

    private Long studentId(HttpServletRequest request) {
        return (Long) request.getAttribute("studentId");
    }
}
