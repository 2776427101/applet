package com.banxing.classassistant.util;

import com.banxing.classassistant.common.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * 微信接口客户端
 * 职责单一：拿 code 换 openid，不掺任何业务逻辑
 */
// 自动生成log对象，下面log.warn/log.error就是它
@Slf4j
// 交给 Spring 管理，别的类可以直接 new 都不用，注入进来就能用
@Component
public class WxApiClient {
    // RestClient 是 Spring 6 之后推荐的同步 HTTP 客户端，比 RestTemplate 更简洁
    private final RestClient restClient = RestClient.create();
    // 小程序 appid，从 yml 读
    private final String appid;
    // 小程序 secret，从 yml 读（只能在后端，绝不能给前端）
    private final String secret;
    // 是否开启 mock：开发阶段设为 true，跳过真实微信请求
    private final boolean mock;
    // mock 模式下固定使用的 openid，从 yml 读，默认指向 seed 里的"李光"老师
    // 为什么必须固定：前端 wx.login() 每次拿到的 code 都不一样，
    // 如果拿 code 当 openid，每登录一次就会在 t_user 里新建一个人，
    // 结果就是永远绑不上班、首页四个数字永远是 0
    private final String mockOpenid;

    public WxApiClient(@Value("${app.wx.appid}") String appid, // 从yml获取appid
                       @Value("${app.wx.secret}") String secret, // 从yml获取secret
                       @Value("${app.wx.mock:false}") boolean mock, // 从yml获取mock开关
                       @Value("${app.wx.mock-openid:mock_openid_teacher}") String mockOpenid) {
        this.appid = appid;
        this.secret = secret;
        this.mock = mock;
        this.mockOpenid = mockOpenid;
    }

    /**
     * 用 wx.login() 拿到的 code 换取 openid
     * @param code 小程序端传来的临时凭证
     * @return 该微信用户的 openid
     */
    @SuppressWarnings("unchecked")
    public String code2Openid(String code) {
        // 开发模式：不管传什么 code，都返回同一个固定 openid
        // 这样前端真调 wx.login() 也能稳定登录成同一个测试用户，不用为 mock 改前端代码
        if (mock) {
            log.warn("微信登录处于 mock 模式，所有 code 都映射到固定 openid：{}", mockOpenid);
            return mockOpenid;
        }
        // 真实调用：微信的 jscode2session 接口
        // 目的：用小程序传来的临时 code，加上后端持有的 appid/secret，向微信服务器换取该用户的 openid
        Map<String, Object> resp = restClient.get()
                .uri("https://api.weixin.qq.com/sns/jscode2session"
                                // 用 {占位符} 而非字符串拼接，由框架负责参数转义，防止特殊字符破坏 URL（安全实践）
                                // appid/secret 是小程序身份凭证，只能留在后端，绝不能下发给前端
                                // js_code 即前端 wx.login() 拿到的临时 code；grant_type 是微信要求的固定值
                                + "?appid={appid}&secret={secret}&js_code={code}&grant_type=authorization_code",
                        appid, secret, code) // 按占位符顺序依次填入真实值
                .retrieve() // 真正发出 HTTP 请求并获取响应
                .body(Map.class); // 将微信返回的 JSON 直接反序列化成 Map（成功含 openid，失败含 errcode）

        if (resp == null || resp.get("openid") == null) {
            // 先把微信原始响应记进 error 日志，方便排查（如 code 过期、appid/secret 错误等具体 errcode）
            log.error("微信 code2session 失败，响应：{}", resp);
            // 抛出业务异常，交给全局异常处理器统一转成友好提示；不把微信原始错误直接暴露给前端（安全考虑）
            throw new BizException(2002, "微信服务异常，请稍后再试");
        }
        // 成功路径：从响应中取出 openid，String.valueOf 兜底把 Object 转为字符串后返回给业务层用于定位/创建用户
        return String.valueOf(resp.get("openid"));
    }
}
