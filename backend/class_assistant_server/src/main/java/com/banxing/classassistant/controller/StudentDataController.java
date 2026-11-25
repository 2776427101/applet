package com.banxing.classassistant.controller;

import com.banxing.classassistant.common.Result;
import com.banxing.classassistant.dto.StudentHomeVO;
import com.banxing.classassistant.dto.StudentLeaveCreateDTO;
import com.banxing.classassistant.dto.StudentLeaveVO;
import com.banxing.classassistant.dto.StudentNoticeListVO;
import com.banxing.classassistant.dto.StudentProfileUpdateDTO;
import com.banxing.classassistant.dto.StudentProfileVO;
import com.banxing.classassistant.dto.StudentScoreVO;
import com.banxing.classassistant.dto.StudentSeatVO;
import com.banxing.classassistant.service.StudentDataService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
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
 * 学生端业务数据
 *
 * <p>和 {@link StudentPortalController} 同一套约定：<b>不收 classId / studentId</b>。
 * 两个值全从 token 里取（拦截器已校验 role=2 并塞进 request），
 * 所以这个类里没有任何一个方法需要判断"你是不是在看别人的数据"。
 *
 * <p>为什么拆成两个 Controller 而不是都塞进 Portal：
 * Portal 管身份（我是谁、和班主任的对话），这里管业务（请假、成绩、座位）。
 * 混在一起以后加接口会越来越难找，而且这个文件会变成 400 行。
 */
@RestController
@RequestMapping("/api/student")
@RequiredArgsConstructor
public class StudentDataController {

    private final StudentDataService studentDataService;

    /**
     * 首页聚合：班级、四个数字、周次、最近一次成绩、我的座位
     * GET /api/student/home
     */
    @GetMapping("/home")
    public Result<StudentHomeVO> home(HttpServletRequest request) {
        return Result.success(studentDataService.home(classId(request), studentId(request)));
    }

    /**
     * 我的请假记录（按提交时间倒序，最多 100 条）
     * GET /api/student/leave
     */
    @GetMapping("/leave")
    public Result<java.util.List<StudentLeaveVO>> leaveList(HttpServletRequest request) {
        return Result.success(studentDataService.leaveList(classId(request), studentId(request)));
    }

    /**
     * 单条请假详情
     * GET /api/student/leave/{id}
     */
    @GetMapping("/leave/{id}")
    public Result<StudentLeaveVO> leaveDetail(HttpServletRequest request, @PathVariable Long id) {
        return Result.success(studentDataService.leaveDetail(classId(request), studentId(request), id));
    }

    /**
     * 提交请假
     * POST /api/student/leave
     * body: { "type":1, "startAt":"2026-11-20 08:00:00", "endAt":"2026-11-20 18:00:00", "reason":"..." }
     */
    @PostMapping("/leave")
    public Result<Map<String, Long>> applyLeave(HttpServletRequest request,
                                                @RequestBody StudentLeaveCreateDTO dto) {
        Long id = studentDataService.applyLeave(classId(request), studentId(request), dto);
        return Result.success(Map.of("id", id));
    }

    /**
     * 撤销申请（只有待审批能撤，撤了就是删掉）
     * POST /api/student/leave/{id}/cancel
     */
    @PostMapping("/leave/{id}/cancel")
    public Result<Void> cancelLeave(HttpServletRequest request, @PathVariable Long id) {
        studentDataService.cancelLeave(classId(request), studentId(request), id);
        return Result.success(null);
    }

    /**
     * 销假（只有已通过能销）
     * POST /api/student/leave/{id}/report
     */
    @PostMapping("/leave/{id}/report")
    public Result<Void> reportLeave(HttpServletRequest request, @PathVariable Long id) {
        studentDataService.reportLeave(classId(request), studentId(request), id);
        return Result.success(null);
    }

    /**
     * 我的成绩：历次考试 + 各科分数 + 班级排名
     * GET /api/student/score
     */
    @GetMapping("/score")
    public Result<StudentScoreVO> score(HttpServletRequest request) {
        return Result.success(studentDataService.score(classId(request), studentId(request)));
    }

    /**
     * 我的座位
     * GET /api/student/seat
     */
    @GetMapping("/seat")
    public Result<StudentSeatVO> seat(HttpServletRequest request) {
        return Result.success(studentDataService.seat(classId(request), studentId(request)));
    }

    /**
     * 我的通知：请假审批结果 + 班主任未读私信
     * GET /api/student/notice?limit=20
     */
    @GetMapping("/notice")
    public Result<StudentNoticeListVO> notice(HttpServletRequest request,
                                              @RequestParam(required = false) Integer limit) {
        return Result.success(studentDataService.notices(
                classId(request), studentId(request), limit));
    }

    /**
     * 我的资料
     * GET /api/student/profile
     */
    @GetMapping("/profile")
    public Result<StudentProfileVO> profile(HttpServletRequest request) {
        return Result.success(studentDataService.profile(classId(request), studentId(request)));
    }

    /**
     * 改联系方式（手机 / 微信 / 宿舍），null 表示不改
     * PUT /api/student/profile
     */
    @PutMapping("/profile")
    public Result<StudentProfileVO> updateProfile(HttpServletRequest request,
                                                  @RequestBody StudentProfileUpdateDTO dto) {
        return Result.success(studentDataService.updateProfile(
                classId(request), studentId(request), dto));
    }

    private static Long classId(HttpServletRequest request) {
        return (Long) request.getAttribute("classId");
    }

    private static Long studentId(HttpServletRequest request) {
        return (Long) request.getAttribute("studentId");
    }
}
