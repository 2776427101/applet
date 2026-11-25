package com.banxing.classassistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.banxing.classassistant.common.BizException;
import com.banxing.classassistant.dto.LotteryCardVO;
import com.banxing.classassistant.dto.MessageSendDTO;
import com.banxing.classassistant.dto.MessageVO;
import com.banxing.classassistant.dto.RelayCardVO;
import com.banxing.classassistant.entity.ContainerMember;
import com.banxing.classassistant.entity.ContainerMessage;
import com.banxing.classassistant.entity.User;
import com.banxing.classassistant.mapper.ContainerMemberMapper;
import com.banxing.classassistant.mapper.ContainerMessageMapper;
import com.banxing.classassistant.mapper.UserMapper;
import com.banxing.classassistant.service.ContainerAuthService;
import com.banxing.classassistant.service.ContainerMessageService;
import com.banxing.classassistant.service.LotteryService;
import com.banxing.classassistant.service.RelayService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 群组聊天实现。
 *
 * <p>两条硬规则，任何一条漏了都会变成安全漏洞：
 * <ol>
 *   <li>拉消息前必须 {@code requireMember} —— 被移出的人不能靠翻历史继续看群里的内容</li>
 *   <li>发消息前必须 {@code assertCanSpeak} —— 禁言要真的拦得住</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class ContainerMessageServiceImpl implements ContainerMessageService {

    /** 一次最多返回多少条。50 条纯文本约 25KB，轮询够用也不会拖慢首屏 */
    private static final int DEFAULT_SIZE = 30;
    private static final int MAX_SIZE = 50;

    private final ContainerMessageMapper messageMapper;
    private final ContainerMemberMapper memberMapper;
    private final UserMapper userMapper;
    private final ContainerAuthService auth;
    /* 卡片状态要现算：接龙人数、抽签结果一直在变，存进消息表就要有人同步，
       同步漏一次卡片上的人数就永久错了 */
    private final RelayService relayService;
    private final LotteryService lotteryService;

    // ---------------------------------------------------------------
    // 拉取
    // ---------------------------------------------------------------

    /**
     * {@code afterId} 和 {@code beforeId} 互斥，同时传时以 {@code afterId} 为准 ——
     * "看看有没有新消息"比"翻历史"更紧急，前端也不该同时传两个。
     */
    @Override
    public List<MessageVO> list(Long userId, Long containerId, Long afterId, Long beforeId, Integer size) {
        auth.requireMember(userId, containerId);

        int limit = (size == null || size < 1) ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        boolean incremental = afterId != null && afterId > 0;

        LambdaQueryWrapper<ContainerMessage> qw = new LambdaQueryWrapper<ContainerMessage>()
                .eq(ContainerMessage::getContainerId, containerId)
                .eq(ContainerMessage::getStatus, ContainerMessage.STATUS_NORMAL);

        if (incremental) {
            qw.gt(ContainerMessage::getId, afterId).orderByAsc(ContainerMessage::getId);
        } else {
            if (beforeId != null && beforeId > 0) {
                qw.lt(ContainerMessage::getId, beforeId);
            }
            // 取"最新 N 条"只能倒序，取完再翻回来，否则拿到的是最老的 N 条
            qw.orderByDesc(ContainerMessage::getId);
        }
        // limit 是钳制过的 int，拼不出非数字内容
        qw.last("LIMIT " + limit);

        List<ContainerMessage> rows = messageMapper.selectList(qw);
        if (!incremental) {
            Collections.reverse(rows);
        }
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }
        return toVOList(rows, userId);
    }

    // ---------------------------------------------------------------
    // 发送
    // ---------------------------------------------------------------

    /**
     * 发消息。
     *
     * <p>被禁言的判定放在最前面：禁言期内连"发出去再撤回"都不该发生。
     * 3005 是契约里专门给"被禁言"留的码，前端据此把输入框置灰而不是弹个普通报错。
     *
     * <p>按 {@code type} 分四种：文本要求正文非空；图片/视频要求有 mediaUrl；
     * 文件要求 mediaUrl + 文件名。媒体消息的正文一律存空串 ——
     * content 是 NOT NULL，存 null 会直接插不进去。
     */
    @Override
    public MessageVO send(Long userId, Long containerId, MessageSendDTO dto) {
        ContainerMember me = auth.assertCanSpeak(containerId, userId);

        int type = dto.getType() == null ? ContainerMessage.TYPE_TEXT : dto.getType();
        String content = dto.getContent() == null ? "" : dto.getContent().trim();
        String mediaUrl = null;
        String fileName = null;
        Long fileSize = null;
        Integer duration = null;

        switch (type) {
            case ContainerMessage.TYPE_TEXT -> {
                if (!StringUtils.hasText(content)) {
                    throw new BizException(1001, "消息不能为空");
                }
                if (content.length() > 500) {
                    throw new BizException(1001, "单条消息最多 500 字");
                }
            }
            case ContainerMessage.TYPE_IMAGE, ContainerMessage.TYPE_VIDEO -> {
                mediaUrl = requireUploadedUrl(dto.getMediaUrl());
                fileName = clipName(dto.getFileName());
                fileSize = dto.getFileSize();
                if (type == ContainerMessage.TYPE_VIDEO) {
                    duration = dto.getDuration();
                }
                content = "";
            }
            case ContainerMessage.TYPE_FILE -> {
                mediaUrl = requireUploadedUrl(dto.getMediaUrl());
                fileName = clipName(dto.getFileName());
                if (!StringUtils.hasText(fileName)) {
                    throw new BizException(1001, "缺少文件名");
                }
                fileSize = dto.getFileSize();
                content = "";
            }
            default -> throw new BizException(1001, "不支持的消息类型");
        }

        ContainerMessage m = new ContainerMessage();
        m.setContainerId(containerId);
        m.setSenderId(userId);
        /* 老师发的一律显式写 1：不写也能靠列默认值兜住，但"靠默认值"这件事
           在别人往这张表插学生消息之后就不成立了 —— 谁漏写谁串号 */
        m.setSenderType(ContainerMessage.FROM_TEACHER);
        m.setContent(content);
        m.setType(type);
        m.setMediaUrl(mediaUrl);
        m.setFileName(fileName);
        m.setFileSize(fileSize);
        m.setDuration(duration);
        m.setStatus(ContainerMessage.STATUS_NORMAL);
        // 显式写时间：不写的话数据库会用 DEFAULT 填，但实体里仍是 null，
        // 返回给前端就成了 createdAt=null，气泡上的时间不显示
        m.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(m);

        User u = userMapper.selectById(userId);
        MessageVO vo = new MessageVO();
        vo.setId(m.getId());
        vo.setSenderId(userId);
        vo.setSenderType(ContainerMessage.FROM_TEACHER);
        vo.setSenderName(auth.displayName(me.getNickname(), u));
        vo.setSenderAvatar(u == null ? null : u.getAvatar());
        vo.setSenderRole(me.getRole());
        vo.setContent(content);
        vo.setType(type);
        vo.setMediaUrl(mediaUrl);
        vo.setFileName(fileName);
        vo.setFileSize(fileSize);
        vo.setDuration(duration);
        vo.setCreatedAt(m.getCreatedAt());
        vo.setMine(true);
        return vo;
    }

    /**
     * 媒体地址只接受本服务上传接口返回的 {@code /files/...}。
     *
     * <p>不校验的话，客户端可以传任意 URL（外部图床、带追踪参数的短链），
     * 群聊就变成了一个可以挂外链的地方；而前端还会把它当图片直接加载，
     * 等于让所有人的小程序去访问一个陌生人给的地址。
     */
    private String requireUploadedUrl(String raw) {
        String url = raw == null ? "" : raw.trim();
        if (!StringUtils.hasText(url)) {
            throw new BizException(1001, "缺少文件地址");
        }
        if (!url.startsWith("/files/") || url.contains("..") || url.contains("\\")) {
            throw new BizException(1001, "文件地址不合法");
        }
        if (url.length() > 500) {
            throw new BizException(1001, "文件地址太长");
        }
        return url;
    }

    /** 文件名只用于展示，去掉路径分隔符并截断，别让它影响渲染 */
    private String clipName(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String name = raw.trim().replace("/", "").replace("\\", "");
        return name.length() > 128 ? name.substring(0, 128) : name;
    }

    // ---------------------------------------------------------------
    // 组装
    // ---------------------------------------------------------------

    /** 批量取发送者信息，避免每条消息两次查询（N+1） */
    private List<MessageVO> toVOList(List<ContainerMessage> rows, Long meId) {
        /* 只有老师消息才需要反查 t_user。学生消息的姓名是发送时存下的快照 ——
           而且学生根本不在 t_user 里，拿 studentId 去 selectBatchIds 会捞到一个
           毫不相干的老师，名字就串了（t_user.id=121 与 t_student.id=121） */
        List<Long> teacherIds = rows.stream()
                .filter(r -> !isFromStudent(r))
                .map(ContainerMessage::getSenderId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<Long, User> users = new HashMap<>();
        if (!teacherIds.isEmpty()) {
            for (User u : userMapper.selectBatchIds(teacherIds)) {
                users.put(u.getId(), u);
            }
        }

        // 群昵称和角色都存在成员表里，一起批量捞
        Map<Long, ContainerMember> members = new HashMap<>();
        if (!teacherIds.isEmpty()) {
            List<ContainerMember> ms = memberMapper.selectList(new LambdaQueryWrapper<ContainerMember>()
                    .eq(ContainerMember::getContainerId, rows.get(0).getContainerId())
                    .in(ContainerMember::getUserId, teacherIds));
            for (ContainerMember m : ms) {
                members.put(m.getUserId(), m);
            }
        }

        List<MessageVO> out = new ArrayList<>(rows.size());
        for (ContainerMessage r : rows) {
            boolean fromStudent = isFromStudent(r);
            User u = fromStudent ? null : users.get(r.getSenderId());
            ContainerMember m = fromStudent ? null : members.get(r.getSenderId());
            MessageVO vo = new MessageVO();
            vo.setId(r.getId());
            vo.setSenderId(r.getSenderId());
            vo.setSenderType(fromStudent ? ContainerMessage.FROM_STUDENT : ContainerMessage.FROM_TEACHER);
            vo.setSenderName(fromStudent
                    ? (StringUtils.hasText(r.getSenderName()) ? r.getSenderName() : "同学")
                    : auth.displayName(m == null ? null : m.getNickname(), u));
            vo.setSenderAvatar(u == null ? null : u.getAvatar());
            vo.setSenderRole(m == null ? null : m.getRole());
            vo.setContent(r.getContent());
            vo.setType(r.getType());
            vo.setRefId(r.getRefId());
            vo.setMediaUrl(r.getMediaUrl());
            vo.setFileName(r.getFileName());
            vo.setFileSize(r.getFileSize());
            vo.setDuration(r.getDuration());
            vo.setCreatedAt(r.getCreatedAt());
            /* mine 必须连角色一起比。老师 id=1 和学生 id=1 是两个人，
               只比 id 会把学生说的话标成"我发的"，而且不报错 */
            vo.setMine(!fromStudent && Objects.equals(r.getSenderId(), meId));
            out.add(vo);
        }
        attachCards(out, rows.get(0).getContainerId(), meId);
        return out;
    }

    /** 这条消息是不是学生发的。老数据没有 sender_type，一律按老师处理 */
    private static boolean isFromStudent(ContainerMessage r) {
        return r.getSenderType() != null && r.getSenderType() == ContainerMessage.FROM_STUDENT;
    }

    /**
     * 给卡片消息补实时状态。
     *
     * <p>两类 id 各批量查一次，<b>不在循环里查</b>：一屏 30 条消息里可能有 5 张卡片，
     * 逐条查就是 5 次往返。这里固定 2 次。
     *
     * <p>接龙/抽签被取消、群被解散这类情况，卡片查不到就不填 ——
     * 前端拿到 {@code relayCard=null} 时降级显示 content（「[接龙] 春游报名」），
     * 不会白屏，也不会显示一张永远"进行中"的假卡。
     */
    private void attachCards(List<MessageVO> list, Long containerId, Long meId) {
        List<Long> relayIds = list.stream()
                .filter(v -> v.getType() != null && v.getType() == ContainerMessage.TYPE_RELAY)
                .map(MessageVO::getRefId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        List<Long> lotteryIds = list.stream()
                .filter(v -> v.getType() != null && v.getType() == ContainerMessage.TYPE_LOTTERY)
                .map(MessageVO::getRefId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (relayIds.isEmpty() && lotteryIds.isEmpty()) {
            return;
        }

        Map<Long, RelayCardVO> relays = relayIds.isEmpty()
                ? Collections.emptyMap()
                : relayService.cardMap(meId, containerId, relayIds);
        Map<Long, LotteryCardVO> lotteries = lotteryIds.isEmpty()
                ? Collections.emptyMap()
                : lotteryService.cardMap(meId, containerId, lotteryIds);

        for (MessageVO v : list) {
            if (v.getRefId() == null) {
                continue;
            }
            if (v.getType() != null && v.getType() == ContainerMessage.TYPE_RELAY) {
                v.setRelayCard(relays.get(v.getRefId()));
            } else if (v.getType() != null && v.getType() == ContainerMessage.TYPE_LOTTERY) {
                v.setLotteryCard(lotteries.get(v.getRefId()));
            }
        }
    }
}
