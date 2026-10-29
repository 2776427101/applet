package com.banxing.classassistant.common;

import lombok.Data;

// Result类，统一返回结果
@Data
public class Result<T> {

    private int code;        // 业务码：0=成功，非 0=失败
    private String message;  // 提示信息，成功固定为 ok
    private T data;          // 泛型生效，返回具体类型，前端拿到的就是 DTO/VO

    private Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    // 成功，code=0、message=ok，这两个值是接口契约里定死的
    public static <T> Result<T> success(T data) {
        return new Result<>(0, "ok", data);
    }

    // 失败，按错误码返回，例如 1002=未登录、3001=无权限
    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }

    // 失败兜底，没分类的内部错误统一用 5000
    public static <T> Result<T> error(String message) {
        return new Result<>(5000, message, null);
    }
}
