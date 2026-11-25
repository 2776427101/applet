package com.banxing.classassistant.controller;

import com.banxing.classassistant.common.Result;
import com.banxing.classassistant.dto.BlacklistVO;
import com.banxing.classassistant.dto.ContainerCreateDTO;
import com.banxing.classassistant.dto.ContainerCreateVO;
import com.banxing.classassistant.dto.ContainerDetailVO;
import com.banxing.classassistant.dto.ContainerListVO;
import com.banxing.classassistant.dto.ContainerLogVO;
import com.banxing.classassistant.dto.ContainerUpdateDTO;
import com.banxing.classassistant.dto.DissolveDTO;
import com.banxing.classassistant.dto.HighlightCreateDTO;
import com.banxing.classassistant.dto.HighlightVO;
import com.banxing.classassistant.dto.InviteExpireDTO;
import com.banxing.classassistant.dto.InviteVO;
import com.banxing.classassistant.dto.JoinPasswordDTO;
import com.banxing.classassistant.dto.MemberListVO;
import com.banxing.classassistant.dto.MemberMuteDTO;
import com.banxing.classassistant.dto.MemberRemoveDTO;
import com.banxing.classassistant.dto.MemberRoleDTO;
import com.banxing.classassistant.service.ContainerMemberService;
import com.banxing.classassistant.service.ContainerService;
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
 * 学科班级容器（教师端）。
 *
 * <p><b>这是本项目第一批"教师端带 {id}"的接口</b>，和 /api/teacher/class 那套
 * "路径不带 id、班级只能从 token 反查"的写法不同 —— 因为一个老师有多个容器，
 * 必须能指定操作哪一个。
 *
 * <p>因此每个方法的第一道防线不是 Controller，而是 Service 里的
 * {@code ContainerAuthService.requireXxx(userId, containerId)}：
 * Controller 只负责取 userId 和转调，<b>不做任何权限判断</b>
 * （写在 Controller 里容易被下一个接口漏抄）。
 *
 * <p>{@code GET /{id}} 和 {@code GET /list} 不会打架：Spring 先匹配字面量 /list。
 * 将来若加 {@code /export} 这类字面量路径，同样没问题；但 {@code /{id}} 的 id
 * 必须是数字，否则会先被当成 id 再转 Long 失败（返回 1001）。
 */
@RestController
@RequestMapping("/api/teacher/container")
@RequiredArgsConstructor
public class ContainerController {

    private final ContainerService containerService;
    private final ContainerMemberService memberService;

    // ---------------------------------------------------------------
    // 容器
    // ---------------------------------------------------------------

    /**
     * 我的学科容器
     * GET /api/teacher/container/list?tab=created&keyword=数学&classId=1&inviteStatus=1
     *
     * @param tab          created=我创建的 / managed=我管理的 / joined=我加入的，默认 created
     * @param keyword      匹配学科名或容器名
     * @param classId      只看关联某个行政班的容器
     * @param inviteStatus 1=有效 2=已过期 3=永久
     */
    @GetMapping("/list")
    public Result<ContainerListVO> list(
            HttpServletRequest request,
            @RequestParam(required = false) String tab,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) Integer inviteStatus) {

        Long userId = (Long) request.getAttribute("userId");
        return Result.success(containerService.list(userId, tab, keyword, classId, inviteStatus));
    }

    /**
     * 创建容器
     * POST /api/teacher/container
     *
     * 返回新容器的 id 和邀请码 —— 老师建完第一件事就是把码发出去。
     */
    @PostMapping
    public Result<ContainerCreateVO> create(
            HttpServletRequest request,
            @Valid @RequestBody ContainerCreateDTO dto) {

        Long userId = (Long) request.getAttribute("userId");
        return Result.success(containerService.create(userId, dto));
    }

    /**
     * 容器详情
     * GET /api/teacher/container/{id}
     */
    @GetMapping("/{id}")
    public Result<ContainerDetailVO> detail(HttpServletRequest request, @PathVariable Long id) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(containerService.detail(userId, id));
    }

    /**
     * 修改容器信息（全量提交，classId 传 null = 解除关联）
     * PUT /api/teacher/container/{id}
     */
    @PutMapping("/{id}")
    public Result<Void> update(
            HttpServletRequest request,
            @PathVariable Long id,
            @Valid @RequestBody ContainerUpdateDTO dto) {

        Long userId = (Long) request.getAttribute("userId");
        containerService.update(userId, id, dto);
        return Result.success(null);
    }

    /**
     * 解散容器（软删）
     * POST /api/teacher/container/{id}/dissolve
     *
     * 用 POST 而不是 DELETE：请求体里要带 confirmName 做二次确认，
     * 而 DELETE 带 body 在小程序端不保险（部分网关会丢掉 body）。
     */
    @PostMapping("/{id}/dissolve")
    public Result<Void> dissolve(
            HttpServletRequest request,
            @PathVariable Long id,
            @Valid @RequestBody DissolveDTO dto) {

        Long userId = (Long) request.getAttribute("userId");
        containerService.dissolve(userId, id, dto);
        return Result.success(null);
    }

    // ---------------------------------------------------------------
    // 成员
    // ---------------------------------------------------------------

    /**
     * 成员列表
     * GET /api/teacher/container/{id}/members?role=2&keyword=张&status=1
     *
     * @param status 不传=只看在群；-1=全部（含已移除/已退出）
     */
    @GetMapping("/{id}/members")
    public Result<MemberListVO> members(
            HttpServletRequest request,
            @PathVariable Long id,
            @RequestParam(required = false) Integer role,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {

        Long userId = (Long) request.getAttribute("userId");
        return Result.success(memberService.members(userId, id, role, keyword, status));
    }

    /**
     * 设为 / 取消班委（只有群主）
     * PUT /api/teacher/container/{id}/members/{userId}/role
     * body: {"role": 2} 或 {"role": 3}
     */
    @PutMapping("/{id}/members/{userId}/role")
    public Result<Void> setRole(
            HttpServletRequest request,
            @PathVariable Long id,
            @PathVariable Long userId,
            @Valid @RequestBody MemberRoleDTO dto) {

        Long operatorId = (Long) request.getAttribute("userId");
        memberService.setRole(operatorId, id, userId, dto);
        return Result.success(null);
    }

    /**
     * 禁言 / 解除禁言（群主 + 班委，班委只能禁言普通成员）
     * PUT /api/teacher/container/{id}/members/{userId}/mute
     * body: {"muteMinutes": 60, "reason": "上课讲话"} / {"muteMinutes": -1} / {"muteMinutes": 0}
     */
    @PutMapping("/{id}/members/{userId}/mute")
    public Result<Void> mute(
            HttpServletRequest request,
            @PathVariable Long id,
            @PathVariable Long userId,
            @Valid @RequestBody MemberMuteDTO dto) {

        Long operatorId = (Long) request.getAttribute("userId");
        memberService.mute(operatorId, id, userId, dto);
        return Result.success(null);
    }

    /**
     * 移除成员（只有群主）
     * DELETE /api/teacher/container/{id}/members/{userId}?blacklist=true&reason=xxx
     *
     * <p><b>参数走 query 而不是 body</b>：DELETE 带 body 在各端行为不一致
     * （H5 的 XHR 会发，小程序的 wx.request 有时会把它拼进 URL），
     * 一旦被吞掉，"是否拉黑"就会静默变成 false —— 这是最危险的那种失败：
     * 老师以为拉黑了，人还能进来。query 参数没有这个歧义。
     */
    @DeleteMapping("/{id}/members/{userId}")
    public Result<Void> remove(
            HttpServletRequest request,
            @PathVariable Long id,
            @PathVariable Long userId,
            @RequestParam(required = false) Boolean blacklist,
            @RequestParam(required = false) String reason) {

        Long operatorId = (Long) request.getAttribute("userId");
        MemberRemoveDTO dto = new MemberRemoveDTO();
        dto.setBlacklist(blacklist);
        dto.setReason(reason);
        memberService.remove(operatorId, id, userId, dto);
        return Result.success(null);
    }

    // ---------------------------------------------------------------
    // 黑名单（只有群主）
    // ---------------------------------------------------------------

    /**
     * 黑名单队列
     * GET /api/teacher/container/{id}/blacklist
     */
    @GetMapping("/{id}/blacklist")
    public Result<List<BlacklistVO>> blacklist(HttpServletRequest request, @PathVariable Long id) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(memberService.blacklist(userId, id));
    }

    /**
     * 解除黑名单
     * DELETE /api/teacher/container/{id}/blacklist/{userId}
     *
     * 解除后这个人<b>不会自动回到群里</b>，需要他自己重新用邀请码加入。
     */
    @DeleteMapping("/{id}/blacklist/{userId}")
    public Result<Void> releaseBlacklist(
            HttpServletRequest request,
            @PathVariable Long id,
            @PathVariable Long userId) {

        Long operatorId = (Long) request.getAttribute("userId");
        memberService.releaseBlacklist(operatorId, id, userId);
        return Result.success(null);
    }

    // ---------------------------------------------------------------
    // 精华消息
    // ---------------------------------------------------------------

    /**
     * 精华列表（任何在群成员都能看）
     * GET /api/teacher/container/{id}/highlights
     */
    @GetMapping("/{id}/highlights")
    public Result<List<HighlightVO>> highlights(HttpServletRequest request, @PathVariable Long id) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(memberService.highlights(userId, id));
    }

    /**
     * 收录精华（群主 + 班委）
     * POST /api/teacher/container/{id}/highlights
     */
    @PostMapping("/{id}/highlights")
    public Result<HighlightVO> addHighlight(
            HttpServletRequest request,
            @PathVariable Long id,
            @Valid @RequestBody HighlightCreateDTO dto) {

        Long userId = (Long) request.getAttribute("userId");
        return Result.success(memberService.addHighlight(userId, id, dto));
    }

    /**
     * 取消精华（群主可取消任意一条，班委只能取消自己的）
     * DELETE /api/teacher/container/{id}/highlights/{highlightId}
     */
    @DeleteMapping("/{id}/highlights/{highlightId}")
    public Result<Void> cancelHighlight(
            HttpServletRequest request,
            @PathVariable Long id,
            @PathVariable Long highlightId) {

        Long userId = (Long) request.getAttribute("userId");
        memberService.cancelHighlight(userId, id, highlightId);
        return Result.success(null);
    }

    // ---------------------------------------------------------------
    // 邀请码 / 加入密码 / 日志
    // ---------------------------------------------------------------

    /**
     * 查看邀请码（群主 + 班委）
     * GET /api/teacher/container/{id}/invite
     */
    @GetMapping("/{id}/invite")
    public Result<InviteVO> invite(HttpServletRequest request, @PathVariable Long id) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(containerService.invite(userId, id));
    }

    /**
     * 改邀请码有效期（只有群主）
     * PUT /api/teacher/container/{id}/invite/expire
     * body: {"days":7} / {"expireAt":"2026-12-31 23:59:59"} / {}
     */
    @PutMapping("/{id}/invite/expire")
    public Result<Void> updateExpire(
            HttpServletRequest request,
            @PathVariable Long id,
            @RequestBody(required = false) InviteExpireDTO dto) {

        Long userId = (Long) request.getAttribute("userId");
        containerService.updateExpire(userId, id, dto == null ? new InviteExpireDTO() : dto);
        return Result.success(null);
    }

    /**
     * 重新生成邀请码（只有群主）。旧码立即失效
     * POST /api/teacher/container/{id}/invite/regenerate
     */
    @PostMapping("/{id}/invite/regenerate")
    public Result<Map<String, String>> regenerate(HttpServletRequest request, @PathVariable Long id) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(Map.of("inviteCode", containerService.regenerateCode(userId, id)));
    }

    /**
     * 设置 / 修改 / 关闭加入密码（只有群主）
     * PUT /api/teacher/container/{id}/join-password
     * body: {"password":"math2026"} 设置；{"password":null} 关闭
     */
    @PutMapping("/{id}/join-password")
    public Result<Void> setJoinPassword(
            HttpServletRequest request,
            @PathVariable Long id,
            // 必须带 @Valid：DTO 上的 @Size(6,20) 只有校验器真的跑了才有意义。
            // 漏掉它的话，老师能设一个 1 位的密码，页面上还以为设置成功了。
            // required=false 允许空 body，此时不触发校验，等价于"关闭密码"。
            @Valid @RequestBody(required = false) JoinPasswordDTO dto) {

        Long userId = (Long) request.getAttribute("userId");
        containerService.setJoinPassword(userId, id, dto == null ? new JoinPasswordDTO() : dto);
        return Result.success(null);
    }

    /**
     * 操作日志（只有群主）
     * GET /api/teacher/container/{id}/logs?page=1&size=20
     */
    @GetMapping("/{id}/logs")
    public Result<List<ContainerLogVO>> logs(
            HttpServletRequest request,
            @PathVariable Long id,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {

        Long userId = (Long) request.getAttribute("userId");
        return Result.success(containerService.logs(userId, id, page, size));
    }
}
