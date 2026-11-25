package com.banxing.classassistant.controller;

import com.banxing.classassistant.common.Result;
import com.banxing.classassistant.dto.DirectMessageThreadVO;
import com.banxing.classassistant.dto.DirectMessageVO;
import com.banxing.classassistant.dto.SendMessageDTO;
import com.banxing.classassistant.dto.StudentInfoVO;
import com.banxing.classassistant.dto.StudentJoinDTO;
import com.banxing.classassistant.dto.StudentJoinVO;
import com.banxing.classassistant.service.DirectMessageService;
import com.banxing.classassistant.service.StudentPortalService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学生端
 *
 * <p>这里所有接口都<b>不接受</b> classId / studentId 参数 —— 两个值全部来自 token，
 * 由拦截器塞进 request。学生改不了自己是哪个班的，也看不了别人的对话。
 */
@RestController
@RequestMapping("/api/student")
@RequiredArgsConstructor
public class StudentPortalController {

    private final StudentPortalService studentPortalService;
    private final DirectMessageService directMessageService;

    /**
     * 我是谁 + 有几条没读
     * GET /api/student/me
     *
     * <p>还没入班时返回的 classId 是 null，只有姓名和学号 —— 入班页靠它显示"你好，XXX"。
     */
    @GetMapping("/me")
    public Result<StudentInfoVO> me(HttpServletRequest request) {
        return Result.success(studentPortalService.me(
                classId(request), studentId(request), studentNo(request), name(request)));
    }

    /**
     * 和班主任的对话
     * GET /api/student/message
     */
    @GetMapping("/message")
    public Result<DirectMessageThreadVO> thread(HttpServletRequest request) {
        return Result.success(directMessageService.studentThread(classId(request), studentId(request)));
    }

    /**
     * 回复班主任
     * POST /api/student/message
     * body: { "content": "..." }
     */
    @PostMapping("/message")
    public Result<DirectMessageVO> send(HttpServletRequest request,
                                        @RequestBody @Valid SendMessageDTO dto) {
        return Result.success(directMessageService.sendByStudent(
                classId(request), studentId(request), dto.getContent()));
    }

    /**
     * 标为已读
     * POST /api/student/message/read
     */
    @PostMapping("/message/read")
    public Result<Integer> read(HttpServletRequest request) {
        return Result.success(directMessageService.markReadByStudent(classId(request), studentId(request)));
    }

    /**
     * 凭邀请码加入班级
     * POST /api/student/join
     * body: { "inviteCode": "628628" }
     *
     * <p>返回一张<b>换了 classId 的新 token</b>，前端存下来之后所有学生接口都按新班走。
     * 这就是"加入群聊"的全部含义 —— 一个班一个群，进了班就在群里。
     *
     * <p><b>未入班的学生也调这个接口</b>：那种 token 里没有 classId，学号和姓名从 claim 取。
     * 花名册里没有这个学号时，会给他新建一行（老师发邀请码即授权）。
     */
    @PostMapping("/join")
    public Result<StudentJoinVO> join(HttpServletRequest request,
                                      @Valid @RequestBody StudentJoinDTO dto) {
        return Result.success(studentPortalService.join(
                classId(request), studentId(request), studentNo(request), name(request), dto));
    }

    private static Long classId(HttpServletRequest request) {
        return (Long) request.getAttribute("classId");
    }

    private static Long studentId(HttpServletRequest request) {
        return (Long) request.getAttribute("studentId");
    }

    /* 这两个只有"未入班"的 token 才有（拦截器从 claim 塞进来） */
    private static String studentNo(HttpServletRequest request) {
        return (String) request.getAttribute("studentNo");
    }

    private static String name(HttpServletRequest request) {
        return (String) request.getAttribute("name");
    }
}
