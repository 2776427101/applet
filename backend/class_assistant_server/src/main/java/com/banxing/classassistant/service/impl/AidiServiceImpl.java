package com.banxing.classassistant.service.impl;

import com.banxing.classassistant.common.BizException;
import com.banxing.classassistant.dto.AidiActDTO;
import com.banxing.classassistant.dto.AidiActionVO;
import com.banxing.classassistant.dto.AidiChatDTO;
import com.banxing.classassistant.dto.AidiReplyVO;
import com.banxing.classassistant.entity.ClassInfo;
import com.banxing.classassistant.service.AidiActions;
import com.banxing.classassistant.service.AidiMemoService;
import com.banxing.classassistant.service.AidiService;
import com.banxing.classassistant.service.AidiTools;
import com.banxing.classassistant.service.ClassService;
import com.banxing.classassistant.util.DeepSeekClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 乐迪的对话编排
 *
 * <p>三件事：拼系统提示词、跑「模型 → 工具 → 模型」的循环、把最后一句话（可能还带一张确认卡）返回。
 *
 * <p><b>写操作不在这里执行。</b> 模型调用写工具时，只走
 * {@link AidiActions#preview} 生成一张卡，真正落库要等老师点确认后调 {@code /aidi/act}。
 * 所以这个类里没有任何 update / insert。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AidiServiceImpl implements AidiService {

    /** 一次提问最多几轮工具调用。超过就收尾，防止模型来回查把 token 烧光 */
    private static final int MAX_ROUNDS = 3;

    /** 带上来的历史最多用几条。老师问的是当下的问题，太长的上下文只会让模型跑偏 */
    private static final int MAX_HISTORY = 8;

    /** 单条消息的字数上限，防止有人塞一整篇文章进来 */
    private static final int MAX_CONTENT = 500;

    /* 只用来解析模型给的 JSON 参数。不注入 Spring 那个 —— 这里不需要任何全局配置，
       少一个构造参数，也就不用担心 Spring Boot 4 换成 Jackson 3 之后 bean 类型对不上 */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DeepSeekClient deepSeekClient;
    private final AidiTools aidiTools;
    private final AidiActions aidiActions;
    private final AidiMemoService aidiMemo;
    private final ClassService classService;

    @Override
    public AidiReplyVO chat(Long userId, AidiChatDTO dto) {
        if (dto == null || dto.getMessages() == null || dto.getMessages().isEmpty()) {
            throw new BizException(1001, "问点什么吧");
        }
        /* 没配 key 就别往下走了。放在最前面，连班都不用查 */
        if (!deepSeekClient.configured()) {
            throw new BizException(DeepSeekClient.ERR_NO_KEY,
                    "乐迪还没接上大脑：后端没有配置 DeepSeek API Key");
        }

        /* 班 id 由后端校验归属，模型永远拿不到别的班的数据 */
        ClassInfo clazz = classService.resolveClass(userId, dto.getClassId());
        if (clazz == null) {
            throw new BizException(1004, "你还没有班级，乐迪暂时没什么可查的");
        }

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(msg("system", systemPrompt(clazz, userId), null));
        messages.addAll(history(dto));

        List<Map<String, Object>> allTools = toolSchema(true);
        List<Map<String, Object>> readOnly = toolSchema(false);

        /* 待确认的动作。一次只允许一个：两个动作一起弹两张卡，
           老师很容易点错那张，而且"确认"这个词就对不上号了 */
        AidiActionVO pending = null;
        int calls = 0;

        for (int round = 0; round < MAX_ROUNDS; round++) {
            /* 已经有待确认动作时只给读工具：防止模型再提一个新动作 */
            Map<String, Object> resp = deepSeekClient.chat(messages, pending == null ? allTools : readOnly);
            Map<String, Object> answer = firstMessage(resp);

            List<Map<String, Object>> toolCalls = asList(answer.get("tool_calls"));
            String content = asText(answer.get("content"));

            if (toolCalls.isEmpty()) {
                return reply(content, calls, pending);
            }

            /* 带 tool_calls 的 assistant 消息必须原样放回上下文，
               否则下一轮模型会看到"tool 结果前面没有对应的调用"，直接报 400 */
            messages.add(msg("assistant", content, toolCalls));

            for (Map<String, Object> call : toolCalls) {
                String callId = asText(call.get("id"));
                Map<String, Object> fn = asMap(call.get("function"));
                String name = asText(fn.get("name"));
                Map<String, Object> args = parseArgs(asText(fn.get("arguments")));

                String result;
                if (AidiMemoService.isMemoTool(name)) {
                    /* 记忆是「助手自己的笔记」，不改业务数据，所以不走确认卡，直接写 */
                    result = aidiMemo.run(name, args, userId, clazz.getId());
                    calls++;
                } else if (aidiActions.isWrite(name)) {
                    if (pending != null) {
                        result = jsonOf(Map.of("status", "already_pending",
                                "note", "已经有一张确认卡在等老师点，先不要提新操作"));
                    } else {
                        try {
                            pending = aidiActions.preview(name, args, clazz.getId());
                            result = jsonOf(Map.of(
                                    "status", "pending_confirm",
                                    "summary", pending.getSummary(),
                                    "note", "确认卡已经弹给老师了，用一句话说明你要做什么即可，不要重复参数"));
                        } catch (BizException e) {
                            /* 校验不过（找不到学生、分数超范围）当成工具结果返回，
                               模型会转述给老师并追问，而不是整轮 500 */
                            result = jsonOf(Map.of("error", e.getMessage() == null ? "这个操作做不了" : e.getMessage()));
                        }
                    }
                } else {
                    result = aidiTools.run(name, args, clazz.getId());
                    calls++;
                }

                log.debug("乐迪调用 {} -> {}", name, result);
                messages.add(msg("tool", result, null, callId));
            }
        }

        /* 轮数用完还没答完：把工具关掉，让它用已经拿到的数据直接说 */
        messages.add(msg("system", "数据已经查完了，现在直接回答老师，不要再查。", null));
        Map<String, Object> finalResp = deepSeekClient.chat(messages, null);
        return reply(asText(firstMessage(finalResp).get("content")), calls, pending);
    }

    @Override
    public AidiReplyVO act(Long userId, AidiActDTO dto) {
        if (dto == null || dto.getAction() == null || !StringUtils.hasText(dto.getAction().getType())) {
            throw new BizException(1001, "没有要执行的操作");
        }
        /* 这里不检查 API Key：动作参数已经解析好了，执行跟模型无关。
           余额用光的那天，已经弹出来的确认卡也应该能点完 */
        ClassInfo clazz = classService.resolveClass(userId, dto.getClassId());
        if (clazz == null) {
            throw new BizException(1004, "你还没有班级");
        }

        String result = aidiActions.execute(
                dto.getAction().getType(), dto.getAction().getParams(), clazz.getId(), userId);

        AidiReplyVO vo = new AidiReplyVO();
        vo.setReply(result);
        vo.setToolCalls(0);
        return vo;
    }

    /* ================================================================
       提示词
       ================================================================ */

    private String systemPrompt(ClassInfo clazz, Long userId) {
        /* 长期记忆拼在后面。没有记忆时 promptBlock 返回空串，这一段就整块不出现 ——
           不然会留一句"你记得："下面空着，模型容易自己编几条补上 */
        String memory = aidiMemo.promptBlock(userId, clazz.getId());

        return """
                你是「乐迪」，一位帮老师管理班级的 AI 助手。说话像一个靠谱的同事：直接、简短、不客套。

                当前班级：%s
                今天：%s
                %s
                你能查这个班的真实数据：学生名单、历次成绩、考试统计、请假记录、待办。
                需要数据时调用工具，不要凭印象回答。

                你还能动手做这些事：加待办、完成待办、审批请假、群发消息、发起接龙、发起抽签、录入成绩。

                规则：
                1. 涉及具体数字或具体的人，先查再说。查不到就直说查不到，绝不编。
                2. 只用工具返回的数据。工具没给的，就说"这个我查不到"。
                3. 纯文本回答，不要用 Markdown（小程序显示不了表格和星号）。列举时每条以「·」开头，一行一条。
                4. 最多 6 行。老师要的是结论，不是分析过程。
                5. 不提工具名、接口、JSON，也不要说"我正在查询"。
                6. 要动手时直接调用对应的写工具，不要问"需要我帮你做吗"——系统会把操作摊成一张确认卡，
                   老师点确认才真正执行。你只要说一句你要做什么。
                   说「我要给…」而不是「已经给你…」：这时候还没落库，说完成了是假的。
                7. 一次只说一件事。不要在同一条回答里提两个动作。
                8. 参数不全就先问清楚（哪个学生、哪次考试、哪一科），不要猜。
                9. 老师说"取消""不用了"时，回一句"好的"，不要调用任何工具。
                10. 上面长期记忆里已经写过的事，直接用，不要再问一遍。
                11. 老师让你"记住…""以后都…"时用 remember；让你"忘掉…"时用 forget。
                    只记长期有效的规矩（习惯、默认值、班里的约定），
                    不记一次性的问题——"张三多少分"是数据，每次现查，不要记。
                """.formatted(clazz.getName(), LocalDate.now(), memory);
    }

    /**
     * 工具声明。描述写清「什么时候用」，模型选工具全靠这段文字。
     *
     * @param withWrite 是否包含写工具。已经有一张确认卡待处理时传 false
     */
    private List<Map<String, Object>> toolSchema(boolean withWrite) {
        List<Map<String, Object>> list = new ArrayList<>();

        list.add(tool("class_overview",
                "本班概况：在读人数、男女分布、最近一次考试、待审批请假数、未完成待办数。"
                        + "问「班里多少人」「有什么要处理的」时用。",
                Map.of(), List.of()));

        list.add(tool("list_students",
                "本班学生名单（姓名、学号、性别）。想不起某个学生叫什么、或要看全班有哪些人时用。",
                Map.of("keyword", prop("string", "姓名或学号的一部分，不填则返回全班")),
                List.of()));

        list.add(tool("student_scores",
                "某个学生最近 5 次考试的各科分数、总分、班内排名和班级平均分。"
                        + "问「某某成绩怎么样」「谁考得好」时用。",
                Map.of("studentName", prop("string", "学生姓名，尽量写全名")),
                List.of("studentName")));

        list.add(tool("exam_summary",
                "某次考试的整体情况：班级平均总分、最高分、各科平均分、各科最高分、前 5 名。"
                        + "不传考试名则用最近一次考试。",
                Map.of("examName", prop("string", "考试名称的一部分，如「期中」「月考」，不填则取最近一次")),
                List.of()));

        list.add(tool("list_exams",
                "本班最近的考试列表（名称、日期、科目）。录成绩之前先查一次，确认是哪次考试、有哪些科目。",
                Map.of(), List.of()));

        list.add(tool("leave_records",
                "请假记录，最近 10 条。",
                Map.of("status", prop("string", "pending=待审批 approved=已通过 rejected=已驳回，不填=全部")),
                List.of()));

        list.add(tool("todo_items",
                "老师的待办事项，最多 10 条。",
                Map.of("done", prop("string", "undone=未完成 done=已完成，不填=全部")),
                List.of()));

        /* 记忆类工具始终给：它不是"动作"，不会弹确认卡，
           有待确认动作时也不影响老师把这张卡点完 */
        addMemoTools(list);

        if (withWrite) {
            addWriteTools(list);
        }
        return list;
    }

    /**
     * 两个记忆工具。它们直接写库（不进确认卡），因为改的是助手自己的笔记，
     * 不碰班级业务数据，而且一句话就能忘掉。
     */
    private void addMemoTools(List<Map<String, Object>> list) {
        list.add(tool("remember",
                "记住一条长期有效的规矩：老师的习惯、默认值、班里的约定。"
                        + "老师说「记住…」「以后都…」「我们班…」时用。"
                        + "不要记一次性的问题（谁考多少分是数据，每次现查）。",
                Map.of(
                        "content", prop("string", "一句话说清，不超过 80 字"),
                        "scope", prop("string", "class=只对这个班有效（默认），all=所有班都适用")),
                List.of("content")));

        list.add(tool("forget",
                "忘掉一条之前记住的规矩。老师说「忘掉…」「别记这个」时用。",
                Map.of("content", prop("string", "要忘掉的那句话里的关键词")),
                List.of("content")));
    }

    /** 七个写动作。参数都用「人能说出来的东西」（姓名、考试名），不用 id —— 模型编 id 是常事 */
    private void addWriteTools(List<Map<String, Object>> list) {
        list.add(tool("create_todo",
                "给老师新增一条待办。老师说「提醒我…」「记一下…」时用。",
                Map.of(
                        "title", prop("string", "待办内容，如「收家长回执」"),
                        "dueDate", prop("string", "截止日期 yyyy-MM-dd，不填表示没有截止")),
                List.of("title")));

        list.add(tool("complete_todo",
                "把一条未完成的待办标为完成。",
                Map.of("title", prop("string", "待办标题里的关键词，用来定位是哪一条")),
                List.of("title")));

        list.add(tool("audit_leave",
                "通过或驳回某个学生待审批的请假。只有一条待审批记录时才能用。",
                Map.of(
                        "studentName", prop("string", "学生姓名"),
                        "decision", prop("string", "approve=通过，reject=驳回"),
                        "remark", prop("string", "审批备注，可不填")),
                List.of("studentName", "decision")));

        list.add(tool("send_group_message",
                "往这个班的班级群发一条消息。",
                Map.of("content", prop("string", "消息正文，最多 500 字")),
                List.of("content")));

        list.add(tool("create_relay",
                "发起一个接龙（默认序号格式，群成员自己填）。",
                Map.of(
                        "title", prop("string", "接龙主题，最多 30 字"),
                        "description", prop("string", "说明，可不填"),
                        "deadline", prop("string", "截止时间 yyyy-MM-dd HH:mm，不填表示不限")),
                List.of("title")));

        list.add(tool("create_lottery",
                "发起抽签并立即开奖。随机点名或随机分组。",
                Map.of(
                        "kind", prop("string", "roll=随机点名 group=随机分组"),
                        "count", prop("integer", "点名时抽几人（1~50）；分组时分几组（2~20）")),
                List.of("kind", "count")));

        Map<String, Object> scoreItem = new LinkedHashMap<>();
        scoreItem.put("type", "object");
        scoreItem.put("properties", Map.of(
                "subject", prop("string", "科目名，必须是这次考试里有的科目"),
                "score", prop("number", "分数")));
        scoreItem.put("required", List.of("subject", "score"));

        Map<String, Object> scoresProp = new LinkedHashMap<>();
        scoresProp.put("type", "array");
        scoresProp.put("description", "要录的科目和分数，可以只给一科");
        scoresProp.put("items", scoreItem);

        list.add(tool("save_scores",
                "给某个学生录入或修改成绩。录之前先用 list_exams 确认考试名称和科目。",
                Map.of(
                        "studentName", prop("string", "学生姓名"),
                        "examName", prop("string", "考试名称，不填用最近一次考试"),
                        "scores", scoresProp),
                List.of("studentName", "scores")));
    }

    private static Map<String, Object> tool(String name, String desc,
                                            Map<String, Object> props, List<String> required) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        params.put("properties", props);
        if (!required.isEmpty()) {
            params.put("required", required);
        }

        Map<String, Object> fn = new LinkedHashMap<>();
        fn.put("name", name);
        fn.put("description", desc);
        fn.put("parameters", params);

        Map<String, Object> t = new LinkedHashMap<>();
        t.put("type", "function");
        t.put("function", fn);
        return t;
    }

    private static Map<String, Object> prop(String type, String desc) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        m.put("description", desc);
        return m;
    }

    /* ================================================================
       历史消息
       ================================================================ */

    private List<Map<String, Object>> history(AidiChatDTO dto) {
        List<Map<String, Object>> clean = new ArrayList<>();
        for (AidiChatDTO.Msg m : dto.getMessages()) {
            if (m == null || !StringUtils.hasText(m.getContent())) {
                continue;
            }
            String role = m.getRole();
            /* 只放行这两种。system 一旦能被前端塞进来，提示词就被改了 */
            if (!"user".equals(role) && !"assistant".equals(role)) {
                continue;
            }
            String content = m.getContent().trim();
            if (content.length() > MAX_CONTENT) {
                content = content.substring(0, MAX_CONTENT);
            }
            clean.add(msg(role, content, null));
        }

        if (clean.isEmpty()) {
            throw new BizException(1001, "问点什么吧");
        }
        if (clean.size() > MAX_HISTORY) {
            clean = new ArrayList<>(clean.subList(clean.size() - MAX_HISTORY, clean.size()));
        }
        /* 首条必须是 user。以 assistant 开头有些兼容接口会直接 400 */
        while (!clean.isEmpty() && !"user".equals(clean.get(0).get("role"))) {
            clean.remove(0);
        }
        if (clean.isEmpty()) {
            throw new BizException(1001, "问点什么吧");
        }
        return clean;
    }

    /* ================================================================
       杂项
       ================================================================ */

    private static Map<String, Object> msg(String role, String content, List<Map<String, Object>> toolCalls) {
        return msg(role, content, toolCalls, null);
    }

    private static Map<String, Object> msg(String role, String content,
                                           List<Map<String, Object>> toolCalls, String toolCallId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", role);
        if (content != null) {
            m.put("content", content);
        } else if (toolCalls != null) {
            /* 带 tool_calls 的 assistant 消息要给 content 一个空串。
               完全不给这个键，部分兼容实现会直接 400 */
            m.put("content", "");
        }
        if (toolCalls != null) {
            m.put("tool_calls", toolCalls);
        }
        if (toolCallId != null) {
            m.put("tool_call_id", toolCallId);
        }
        return m;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstMessage(Map<String, Object> resp) {
        Object choices = resp == null ? null : resp.get("choices");
        if (!(choices instanceof List<?> list) || list.isEmpty()) {
            throw new BizException(DeepSeekClient.ERR_UPSTREAM, "乐迪没有返回内容，请再问一次");
        }
        if (!(list.get(0) instanceof Map<?, ?> choice)) {
            throw new BizException(DeepSeekClient.ERR_UPSTREAM, "乐迪返回的格式不对，请再问一次");
        }
        Object message = choice.get("message");
        if (!(message instanceof Map<?, ?> m)) {
            throw new BizException(DeepSeekClient.ERR_UPSTREAM, "乐迪返回的格式不对，请再问一次");
        }
        return (Map<String, Object>) m;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> asList(Object o) {
        if (o instanceof List<?> l) {
            return (List<Map<String, Object>>) l;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    private static String asText(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String jsonOf(Map<String, Object> m) {
        try {
            return MAPPER.writeValueAsString(m);
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * 模型给的参数是 JSON 字符串。值保留原始类型 —— 成绩是数组、
     * 分数是数字，全转成字符串的话写工具就没法用了。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArgs(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(raw, Map.class);
        } catch (Exception e) {
            /* 参数不是合法 JSON 时不要让整轮失败：当成"没给参数"，
               工具那边自然会回一句缺什么 */
            log.warn("乐迪工具参数解析失败：{}", raw);
            return Map.of();
        }
    }

    private AidiReplyVO reply(String content, int calls, AidiActionVO action) {
        String text = StringUtils.hasText(content) ? content.trim() : "我没太理解，换个问法再试试？";
        AidiReplyVO vo = new AidiReplyVO();
        vo.setReply(text);
        vo.setToolCalls(calls);
        vo.setAction(action);
        return vo;
    }
}
