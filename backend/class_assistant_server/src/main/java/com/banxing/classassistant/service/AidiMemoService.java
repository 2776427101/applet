package com.banxing.classassistant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.banxing.classassistant.entity.AidiMemo;
import com.banxing.classassistant.mapper.AidiMemoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 乐迪的长期记忆（事实层）
 *
 * <p>存的不是对话，是「稳定事实」：周三下午没课、录成绩默认用最近一次考试、
 * 班费由班长收。这些东西每轮都拼进系统提示词，模型不用调工具就能"想起来"。
 *
 * <p><b>为什么这两个写工具不走两段式确认。</b>
 * 确认卡是为"改错了没撤销按钮"设计的——审批请假、群发消息发出去就收不回。
 * 记忆不一样：它只影响助手自己的行为，不碰班级业务数据，而且一句话就能忘掉。
 * 让老师为了"记住周三没课"再点一次确认，属于自找麻烦。
 * 界线是：<b>改业务数据要确认，改助手自己的笔记不用。</b>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AidiMemoService {

    /** 一个人最多记多少条。超过就拒绝，让老师先清理 —— 悄悄淘汰旧的更糟 */
    private static final int MAX_MEMOS = 30;

    /** 一条记忆的长度上限。记忆该是一句话，不是一段话 */
    private static final int MAX_LEN = 80;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AidiMemoMapper memoMapper;

    /** 是不是记忆类工具（不走 AidiActions 的两段式） */
    public static boolean isMemoTool(String name) {
        return "remember".equals(name) || "forget".equals(name);
    }

    /**
     * 执行一个记忆工具
     *
     * @return JSON 字符串，直接作为工具结果喂回模型
     */
    public String run(String name, Map<String, Object> args, Long userId, Long classId) {
        try {
            if ("remember".equals(name)) {
                return remember(userId, classId, AidiTools.str(args, "content"),
                        AidiTools.str(args, "scope"));
            }
            if ("forget".equals(name)) {
                return forget(userId, classId, AidiTools.str(args, "content"));
            }
            return err("没有这个工具");
        } catch (Exception e) {
            log.warn("乐迪记忆工具 {} 执行失败，userId={}", name, userId, e);
            return err("记不下来，稍后再试");
        }
    }

    /* ================================================================
       拼进系统提示词
       ================================================================ */

    /**
     * 把记得的事实拼成一段提示词。没有记忆时返回空串，调用方据此决定要不要加这一块。
     */
    public String promptBlock(Long userId, Long classId) {
        List<AidiMemo> memos = active(userId, classId);
        if (memos.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("\n关于这位老师和这个班，你记得这些固定事实（长期记忆，不是这次对话里说的）：\n");
        for (AidiMemo m : memos) {
            sb.append("· ").append(m.getContent()).append('\n');
        }
        sb.append("""
                这些是背景，不用主动复述。和老师这次说的冲突时，以这次说的为准。
                老师让你记住新的东西就用 remember；让你忘掉什么就用 forget。
                """);
        return sb.toString();
    }

    /* ================================================================
       remember / forget
       ================================================================ */

    private String remember(Long userId, Long classId, String raw, String scope) {
        String content = normalize(raw);
        if (content == null) {
            return err("没说要记什么");
        }
        if (content.length() > MAX_LEN) {
            return err("这条太长了（" + content.length() + " 字），一条记忆控制在一句话以内（"
                    + MAX_LEN + " 字），把不重要的部分去掉");
        }

        boolean global = isGlobal(scope);
        Long scopeClassId = global ? 0L : classId;

        /* 上限按"这个人所有记忆"算，不按班算：否则每多一个班就多 30 条 */
        long total = memoMapper.selectCount(new LambdaQueryWrapper<AidiMemo>()
                .eq(AidiMemo::getUserId, userId)
                .eq(AidiMemo::getStatus, 1));
        AidiMemo same = memoMapper.selectOne(new LambdaQueryWrapper<AidiMemo>()
                .eq(AidiMemo::getUserId, userId)
                .eq(AidiMemo::getClassId, scopeClassId)
                .eq(AidiMemo::getContent, content));

        if (same != null && Integer.valueOf(1).equals(same.getStatus())) {
            return ok("这条已经记过了：" + content);
        }
        if (same != null) {
            /* 忘掉过又重新记：复活原来那行，不新插一条（唯一键也不允许） */
            same.setStatus(1);
            memoMapper.updateById(same);
            return ok("又想起来了：" + content);
        }
        if (total >= MAX_MEMOS) {
            return err("已经记了 " + total + " 条，太多了。先让我忘掉几条不用的");
        }

        AidiMemo memo = new AidiMemo();
        memo.setUserId(userId);
        memo.setClassId(scopeClassId);
        memo.setContent(content);
        memo.setStatus(1);
        memoMapper.insert(memo);
        log.info("乐迪记住一条：userId={} classId={} content={}", userId, scopeClassId, content);
        return ok("记住了：" + content + (global ? "（跨班通用）" : ""));
    }

    private String forget(Long userId, Long classId, String raw) {
        String keyword = normalize(raw);
        if (keyword == null) {
            return err("没说要忘掉什么");
        }
        List<AidiMemo> hits = memoMapper.selectList(new LambdaQueryWrapper<AidiMemo>()
                .eq(AidiMemo::getUserId, userId)
                .eq(AidiMemo::getStatus, 1)
                .and(w -> w.eq(AidiMemo::getClassId, classId).or().eq(AidiMemo::getClassId, 0))
                .like(AidiMemo::getContent, keyword));

        if (hits.isEmpty()) {
            return err("没记过和「" + keyword + "」有关的事");
        }
        if (hits.size() > 1) {
            /* 不止一条就列出来问，绝不猜 —— 忘错了老师不会发现，
               下次助手的行为就不对了，这种错最难查 */
            List<String> names = new ArrayList<>();
            for (AidiMemo m : hits) {
                names.add(m.getContent());
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("status", "ambiguous");
            m.put("candidates", names);
            m.put("note", "有多条相关记忆，问老师要忘掉哪一条，不要自己选");
            return json(m);
        }

        AidiMemo memo = hits.get(0);
        memo.setStatus(0);
        memoMapper.updateById(memo);
        return ok("忘掉了：" + memo.getContent());
    }

    /* ================================================================
       杂项
       ================================================================ */

    /** 有效的记忆：本班的 + 跨班通用的，本班的排前面 */
    private List<AidiMemo> active(Long userId, Long classId) {
        return memoMapper.selectList(new LambdaQueryWrapper<AidiMemo>()
                .eq(AidiMemo::getUserId, userId)
                .eq(AidiMemo::getStatus, 1)
                .and(w -> w.eq(AidiMemo::getClassId, classId).or().eq(AidiMemo::getClassId, 0))
                .orderByDesc(AidiMemo::getClassId)
                .orderByAsc(AidiMemo::getId)
                .last("LIMIT " + MAX_MEMOS));
    }

    /** 去掉首尾空白、合并连续空白、去掉句尾句号 —— 同义的一句话要能命中同一条 */
    private static String normalize(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String s = raw.replaceAll("\\s+", " ").trim();
        while (s.endsWith("。") || s.endsWith(".")) {
            s = s.substring(0, s.length() - 1).trim();
        }
        return s.isEmpty() ? null : s;
    }

    private static boolean isGlobal(String scope) {
        if (!StringUtils.hasText(scope)) {
            return false;
        }
        String s = scope.trim().toLowerCase();
        return s.startsWith("all") || s.startsWith("global") || s.contains("通用")
                || s.contains("跨班") || s.contains("所有班");
    }

    private static String ok(String msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", "ok");
        m.put("message", msg);
        return json(m);
    }

    private static String err(String msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", msg);
        return json(m);
    }

    private static String json(Map<String, Object> m) {
        try {
            return MAPPER.writeValueAsString(m);
        } catch (Exception e) {
            return "{}";
        }
    }
}
