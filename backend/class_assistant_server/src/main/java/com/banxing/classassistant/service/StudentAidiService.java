package com.banxing.classassistant.service;

import com.banxing.classassistant.common.BizException;
import com.banxing.classassistant.dto.AidiReplyVO;
import com.banxing.classassistant.dto.StudentAidiChatDTO;
import com.banxing.classassistant.dto.StudentLeaveVO;
import com.banxing.classassistant.dto.StudentProfileVO;
import com.banxing.classassistant.dto.StudentScoreVO;
import com.banxing.classassistant.dto.StudentSeatVO;
import com.banxing.classassistant.util.DeepSeekClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 学生端的乐迪
 *
 * <p>和老师端 {@link AidiService} 是<b>两套独立的实现</b>，不是同一个类加个 if。
 * 原因：老师端的工具是「查全班请假」「查全班成绩」「发通知」，每一个都会越权；
 * 而学生端能查的只有他自己那一行。共用一套工具再加权限判断，
 * 等于在每个工具里都写一遍"这个数据是不是他的"——写漏一个就是事故。
 *
 * <p>所以这里重新定义工具集，四个全部只读、全部以 studentId 为参数：
 * {@code my_leave} / {@code my_score} / {@code my_seat} / {@code my_info}。
 * 模型拿不到"指定某个学生"的入参，也就编不出越权的调用。
 *
 * <p>没有记忆层（老师端那个 t_aidi_memo）。学生问的多是"我上次考多少"，
 * 这些都能查，不需要助手额外记什么。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StudentAidiService {

    /**
     * 最多几轮工具调用。
     *
     * <p>原来是 3，改成 2：一次问答的耗时几乎全在"串行等模型"上，
     * 每多一轮就多一次完整的 HTTP 往返（1~3 秒）。学生的问题都是
     * "我上次考多少""我请过几次假"这种一轮就能查完的，
     * 给到第 3 轮只会让它在查不到的时候继续瞎试，越试越慢。
     *
     * <p>2 轮足够：第 1 轮决定查什么，第 2 轮拿到数据后回答。
     * 第 2 轮还在要工具，说明它没查明白，这时候关掉工具让它用已有数据说话
     * （见下面那段兜底），比再放一轮更准也更快。
     */
    private static final int MAX_ROUNDS = 2;

    /** 只带最近 8 条历史。再多既贵又容易跑题 */
    private static final int MAX_HISTORY = 8;

    /** 单条消息最长 500 字，防止有人把整篇作文贴进来 */
    private static final int MAX_CONTENT = 500;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DeepSeekClient deepSeekClient;
    private final StudentDataService studentData;

    public AidiReplyVO chat(Long classId, Long studentId, StudentAidiChatDTO dto) {
        if (dto == null || dto.getMessages() == null || dto.getMessages().isEmpty()) {
            throw new BizException(1001, "问点什么吧");
        }
        if (!deepSeekClient.configured()) {
            throw new BizException(DeepSeekClient.ERR_NO_KEY,
                    "乐迪还没接上大脑：后端没有配置 DeepSeek API Key");
        }

        /* 这一步顺带完成了鉴权：拿不到 profile 就说明 token 里的学生不存在或不在这个班 */
        StudentProfileVO me = studentData.profile(classId, studentId);

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(msg("system", systemPrompt(me), null));
        messages.addAll(history(dto));

        List<Map<String, Object>> tools = toolSchema();
        int calls = 0;
        /* 记耗时：用户说"慢"的时候，日志里得有据可查 ——
           是模型上游慢，还是我们在本地绕了太多轮 */
        long t0 = System.currentTimeMillis();
        int rounds = 0;

        for (int round = 0; round < MAX_ROUNDS; round++) {
            rounds++;
            Map<String, Object> resp = deepSeekClient.chat(messages, tools);
            Map<String, Object> answer = firstMessage(resp);

            List<Map<String, Object>> toolCalls = asList(answer.get("tool_calls"));
            String content = asText(answer.get("content"));

            if (toolCalls.isEmpty()) {
                log.info("学生乐迪：{}ms，{} 轮，{} 次工具", System.currentTimeMillis() - t0, rounds, calls);
                return reply(content, calls);
            }

            /* 带 tool_calls 的 assistant 消息必须原样放回上下文，
               否则下一轮模型会看到"tool 结果前面没有对应的调用"，直接报 400 */
            messages.add(msg("assistant", content, toolCalls));

            for (Map<String, Object> call : toolCalls) {
                String callId = asText(call.get("id"));
                Map<String, Object> fn = asMap(call.get("function"));
                String name = asText(fn.get("name"));
                Map<String, Object> args = parseArgs(asText(fn.get("arguments")));

                String result = run(name, args, classId, studentId);
                calls++;
                log.debug("学生乐迪调用 {} -> {}", name, result);
                messages.add(msg("tool", result, null, callId));
            }
        }

        /* 轮数用完还没答完：把工具关掉，让它用已经拿到的数据直接说 */
        messages.add(msg("system", "数据已经查完了，现在直接回答学生，不要再查。", null));
        Map<String, Object> finalResp = deepSeekClient.chat(messages, null);
        log.info("学生乐迪：{}ms，{} 轮（含收尾），{} 次工具",
                System.currentTimeMillis() - t0, rounds + 1, calls);
        return reply(asText(firstMessage(finalResp).get("content")), calls);
    }

    /* ================================================================
       工具
       ================================================================ */

    /**
     * 执行一个工具。
     *
     * <p><b>注意每个分支都不接受"查谁"</b>：classId / studentId 是方法参数，
     * 不是 args 里的字段。模型就算在 args 里塞一个 studentId 也会被忽略。
     */
    private String run(String name, Map<String, Object> args, Long classId, Long studentId) {
        try {
            switch (name) {
                case "my_leave": {
                    Integer status = asInt(args.get("status"));
                    List<StudentLeaveVO> all = studentData.leaveList(classId, studentId);
                    List<Map<String, Object>> out = new ArrayList<>();
                    for (StudentLeaveVO l : all) {
                        if (status != null && status >= 0 && !status.equals(l.getStatus())) {
                            continue;
                        }
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("id", l.getId());
                        m.put("类型", typeText(l.getType()));
                        m.put("开始", l.getStartAt());
                        m.put("结束", l.getEndAt());
                        m.put("状态", statusText(l.getStatus()));
                        m.put("原因", l.getReason());
                        m.put("审批备注", l.getAuditRemark());
                        out.add(m);
                    }
                    return json(Map.of("条数", out.size(), "记录", out));
                }
                case "my_score": {
                    StudentScoreVO vo = studentData.score(classId, studentId);
                    List<Map<String, Object>> out = new ArrayList<>();
                    for (StudentScoreVO.ExamScore e : vo.getList()) {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("考试", e.getName());
                        m.put("日期", e.getExamDate());
                        m.put("总分", e.getTotal());
                        m.put("满分", e.getFullTotal());
                        m.put("排名", e.getRank());
                        m.put("班级人数", e.getClassSize());
                        m.put("班级平均", e.getClassAvg());
                        m.put("各科", e.getScores());
                        out.add(m);
                    }
                    return json(Map.of("考试次数", out.size(), "成绩", out));
                }
                case "my_seat": {
                    StudentSeatVO s = studentData.seat(classId, studentId);
                    if (!s.isSeated()) {
                        return json(Map.of("已排座", false, "说明", "班主任还没排座位"));
                    }
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("已排座", true);
                    m.put("第几排", s.getRowNo());
                    m.put("第几列", s.getColNo());
                    m.put("教室排数", s.getRows());
                    m.put("教室列数", s.getCols());
                    return json(m);
                }
                case "my_info": {
                    StudentProfileVO p = studentData.profile(classId, studentId);
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("姓名", p.getName());
                    m.put("学号", p.getStudentNo());
                    m.put("班级", p.getClassName());
                    m.put("班主任", p.getTeacherName());
                    m.put("宿舍", p.getDormitory());
                    return json(m);
                }
                default:
                    return json(Map.of("error", "没有这个工具"));
            }
        } catch (BizException e) {
            /* 查不到不等于整轮失败：把话交回给模型，它会转述并追问 */
            return json(Map.of("error", e.getMessage() == null ? "查不到" : e.getMessage()));
        } catch (Exception e) {
            log.warn("学生乐迪工具 {} 出错", name, e);
            return json(Map.of("error", "查询出错了"));
        }
    }

    private List<Map<String, Object>> toolSchema() {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(tool("my_leave", "查我自己的请假记录（含状态、审批备注）。学生问「我请过几次假」「上次请假批了吗」时用。",
                Map.of("status", prop("integer", "可选：0=待审批 1=已通过 2=已驳回 3=已销假，不传=全部")),
                List.of()));
        list.add(tool("my_score", "查我自己的历次考试成绩、各科分数、班级排名。学生问分数或排名时用。",
                Map.of(), List.of()));
        list.add(tool("my_seat", "查我自己的座位（第几排第几列）。",
                Map.of(), List.of()));
        list.add(tool("my_info", "查我自己的基本信息：学号、班级、班主任、宿舍。",
                Map.of(), List.of()));
        return list;
    }

    private static Map<String, Object> tool(String name, String desc,
                                            Map<String, Object> props, List<String> required) {
        Map<String, Object> fn = new LinkedHashMap<>();
        fn.put("name", name);
        fn.put("description", desc);
        fn.put("parameters", Map.of("type", "object", "properties", props, "required", required));
        return Map.of("type", "function", "function", fn);
    }

    private static Map<String, Object> prop(String type, String desc) {
        return Map.of("type", type, "description", desc);
    }

    private String systemPrompt(StudentProfileVO me) {
        return """
                你是「乐迪」，学生端的小助手。你在跟一位学生说话。

                关于他：%s，%s，学号 %s，班主任是 %s。今天是 %s。

                规则：
                1. 只回答他自己的事。他看不到别的同学的分数、请假、座位——问到了就直接说「这个我看不到」。
                2. 数据一律用工具查，不要凭印象答。工具没返回的，就是没有，不要编分数和排名。
                3. 你只能查，不能改。他要请假、销假、改资料，就告诉他去哪个页面点（请假在底部「请假」，资料在「我的」）。
                4. 说话短：3~5 行，像同学之间讲话。不要用 markdown 表格、标题、加粗。
                5. 分数、日期这类数字原样说，不要四舍五入。
                """.formatted(
                me.getName(), me.getClassName(), me.getStudentNo(), me.getTeacherName(),
                LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy年M月d日")));
    }

    /* ================================================================
       杂项
       ================================================================ */

    private List<Map<String, Object>> history(StudentAidiChatDTO dto) {
        List<Map<String, Object>> out = new ArrayList<>();
        List<StudentAidiChatDTO.Msg> src = dto.getMessages();
        int from = Math.max(0, src.size() - MAX_HISTORY);
        for (int i = from; i < src.size(); i++) {
            StudentAidiChatDTO.Msg m = src.get(i);
            if (m == null || m.getContent() == null || m.getContent().isBlank()) {
                continue;
            }
            /* 只认 user / assistant。前端如果传了 system，
               等于把"你是谁"交给客户端决定，那提示词就白写了 */
            String role = m.getRole();
            if (!"user".equals(role) && !"assistant".equals(role)) {
                continue;
            }
            String content = m.getContent().trim();
            if (content.length() > MAX_CONTENT) {
                content = content.substring(0, MAX_CONTENT);
            }
            out.add(msg(role, content, null));
        }
        return out;
    }

    private static Map<String, Object> msg(String role, String content, List<Map<String, Object>> toolCalls) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", role);
        m.put("content", content == null ? "" : content);
        if (toolCalls != null && !toolCalls.isEmpty()) {
            m.put("tool_calls", toolCalls);
        }
        return m;
    }

    private static Map<String, Object> msg(String role, String content,
                                           List<Map<String, Object>> toolCalls, String toolCallId) {
        Map<String, Object> m = msg(role, content, toolCalls);
        if (toolCallId != null) {
            m.put("tool_call_id", toolCallId);
        }
        return m;
    }

    private static Map<String, Object> firstMessage(Map<String, Object> resp) {
        List<Map<String, Object>> choices = asList(resp == null ? null : resp.get("choices"));
        if (choices.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> m = asMap(choices.get(0).get("message"));
        return m == null ? Map.of() : m;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> asList(Object o) {
        if (o instanceof List<?> l) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object item : l) {
                if (item instanceof Map<?, ?> m) {
                    out.add((Map<String, Object>) m);
                }
            }
            return out;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    private static String asText(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static Integer asInt(Object o) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        if (o instanceof String s && !s.isBlank()) {
            try {
                return Integer.valueOf(s.trim());
            } catch (NumberFormatException ignore) {
                return null;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseArgs(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(raw, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static String json(Object o) {
        try {
            return MAPPER.writeValueAsString(o);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static String typeText(Integer t) {
        if (t == null) {
            return "请假";
        }
        return switch (t) {
            case 1 -> "病假";
            case 2 -> "事假";
            default -> "其他";
        };
    }

    private static String statusText(Integer s) {
        if (s == null) {
            return "未知";
        }
        return switch (s) {
            case 0 -> "待审批";
            case 1 -> "已通过";
            case 2 -> "已驳回";
            case 3 -> "已销假";
            default -> "未知";
        };
    }

    private static AidiReplyVO reply(String content, int calls) {
        AidiReplyVO vo = new AidiReplyVO();
        vo.setReply(content == null || content.isBlank()
                ? "我没查到相关的记录。换个说法再问一次？"
                : content.trim());
        vo.setToolCalls(calls);
        return vo;
    }
}
