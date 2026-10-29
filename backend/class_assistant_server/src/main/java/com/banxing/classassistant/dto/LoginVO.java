package com.banxing.classassistant.dto;

import lombok.Data;

/**
 * 登录成功后返回给前端的数据
 */
@Data
public class LoginVO {

    // JWT 令牌，前端存到 storage，之后每个请求放进 Authorization 头
    private String token;

    // 有效期（秒），前端可以用它判断是否需要提前续期
    private long expiresIn;

    // 是否还需要绑定班级：true 表示这个用户还没进任何班
    private boolean needBindClass;

    // 用户基本信息
    private UserVO user;

    /**
     * 用户信息，只暴露前端需要的字段，这是一个嵌套类，给user用的
     * 注意：openid、unionid 这些敏感字段绝对不能出现在这里
     */
    @Data
    public static class UserVO {
        private Long id;
        private String name;
        private Integer role;
        private String avatar;
        private Long classId;
        private String className;
    }
}