package com.banxing.classassistant.common;

import lombok.Getter;
/**
 * 业务异常，用于表示业务逻辑错误
 * 与系统异常（如空指针、数据库连接失败）区分开：
 * 业务异常是可预期的、由代码主动抛出的错误（如"用户名已存在""token 已过期"），
 * 其消息可以直接返回给前端展示，不包含堆栈等内部实现细节，避免泄露敏感信息。
 */
@Getter // Lombok 注解：编译时自动生成 getCode()，供全局异常处理器读取错误码
public class BizException extends RuntimeException {

    // 业务错误码：前端根据 code 区分错误类型（如 401 未登录、4001 参数错误）。
    // 用 final 修饰，保证异常创建后错误码不可被篡改
    private final int code;

    /**
     * 构造业务异常
     *
     * @param code    业务错误码，会随响应体一起返回给前端
     * @param message 错误提示信息，直接面向用户展示，不应包含 SQL、堆栈等敏感内容
     */
    public BizException(int code, String message) {
        // 将消息交给父类 RuntimeException 保存，异常日志中可正常输出
        super(message);
        this.code = code;
    }
}
