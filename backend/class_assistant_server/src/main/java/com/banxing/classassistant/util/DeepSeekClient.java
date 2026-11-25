package com.banxing.classassistant.util;

import com.banxing.classassistant.common.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DeepSeek 客户端
 *
 * <p>职责单一：把「一组 messages + 一组 tools」发出去，拿回一条 assistant message。
 * 工具怎么选、数据怎么查、话怎么说，都不归它管。
 *
 * <p><b>为什么不流式（stream）</b>：小程序端要边收边渲染，得用
 * {@code wx.request({ enableChunked: true })} 自己解析 SSE，还要处理分片粘包。
 * 第一版先做「问完一次性返回」，短答案（6 行以内）等 2~4 秒是可接受的，
 * 换来的是后端一个普通 JSON 接口、前端一个普通请求。
 */
@Slf4j
@Component
public class DeepSeekClient {

    /** 没配置 key。前端要把这句原样显示出来，所以文案要能直接给人看 */
    public static final int ERR_NO_KEY = 5001;

    /** key 无效 / 余额不足 */
    public static final int ERR_KEY = 5002;

    /** DeepSeek 挂了 / 超时 / 返回空 */
    public static final int ERR_UPSTREAM = 5003;

    /** 被限流 */
    public static final int ERR_RATE = 5004;

    private final RestClient restClient;
    private final String apiKey;
    private final String model;
    private final String endpoint;

    public DeepSeekClient(@Value("${app.deepseek.api-key:}") String apiKey,
                          @Value("${app.deepseek.base-url:https://api.deepseek.com}") String baseUrl,
                          @Value("${app.deepseek.model:deepseek-chat}") String model,
                          @Value("${app.deepseek.timeout-seconds:60}") int timeoutSeconds) {

        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = StringUtils.hasText(model) ? model.trim() : "deepseek-chat";

        /* base-url 允许配成 https://api.deepseek.com 或 .../v1，
           末尾多一个斜杠不该导致拼出 //chat/completions */
        String base = StringUtils.hasText(baseUrl) ? baseUrl.trim() : "https://api.deepseek.com";
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        this.endpoint = base + "/chat/completions";

        /* 必须显式设超时。RestClient 默认没有读超时，
           模型卡住时 Tomcat 线程会一直挂着，几个并发就能把线程池占满 */
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(8));
        factory.setReadTimeout(Duration.ofSeconds(Math.max(10, timeoutSeconds)));

        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    /** 配了 key 才能问。没配时不要在启动期报错 —— 别的功能还得能用 */
    public boolean configured() {
        return StringUtils.hasText(apiKey);
    }

    /**
     * 发一次对话请求
     *
     * @param messages 完整上下文，最后一条必须是 user 或 tool
     * @param tools    工具声明；传 null / 空表示这一轮不给工具（收尾总结时用）
     * @return 原始响应体，调用方自己去取 choices[0].message
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> chat(List<Map<String, Object>> messages, List<Map<String, Object>> tools) {
        if (!configured()) {
            throw new BizException(ERR_NO_KEY, "乐迪还没配置 DeepSeek API Key");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        /* 0.3 而不是默认 1.0：这是查数据，不是写文案。
           温度高一点它就开始"合理推测"没查到的分数 */
        body.put("temperature", 0.3);
        body.put("max_tokens", 900);
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools);
            /* auto：由模型自己决定要不要查。强制 required 会让"你好"也去查一遍数据库 */
            body.put("tool_choice", "auto");
        }

        try {
            Map<String, Object> resp = restClient.post()
                    .uri(endpoint)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            if (resp == null) {
                throw new BizException(ERR_UPSTREAM, "乐迪没有返回内容，请再问一次");
            }
            return resp;

        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            /* 原始响应必须进日志：401 是 key 错，402 是没钱，400 往往是 tools 的 schema 写错了，
               这几种在业务上完全不是一回事，但前端只会看到一句话 */
            log.error("DeepSeek 返回 {}：{}", status, e.getResponseBodyAsString());

            if (status == 401 || status == 403) {
                throw new BizException(ERR_KEY, "DeepSeek API Key 无效，请检查配置");
            }
            if (status == 402) {
                throw new BizException(ERR_KEY, "DeepSeek 账户余额不足");
            }
            if (status == 429) {
                throw new BizException(ERR_RATE, "问得太快啦，歇几秒再问");
            }
            throw new BizException(ERR_UPSTREAM, "DeepSeek 服务异常（" + status + "），请稍后再试");

        } catch (ResourceAccessException e) {
            /* 超时和连不上都走这里。分开提示没有意义 —— 老师能做的都是"再试一次" */
            log.error("连接 DeepSeek 失败：{}", e.getMessage());
            throw new BizException(ERR_UPSTREAM, "连不上 DeepSeek，请检查服务器网络");
        }
    }
}
