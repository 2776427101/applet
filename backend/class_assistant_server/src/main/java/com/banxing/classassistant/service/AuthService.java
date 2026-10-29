package com.banxing.classassistant.service;

import com.banxing.classassistant.dto.LoginVO;

/**
 * 登录相关业务
 */
public interface AuthService {

    /**
     * 微信登录：code 换 openid，查/建用户，签发 token
     * @param code wx.login() 拿到的临时凭证
     * @return 登录结果（token + 用户信息）
     */
    LoginVO login(String code);

    /**
     * 查询当前登录用户信息
     * @param userId 拦截器解析出的用户 ID
     * @return 用户信息
     */
    LoginVO.UserVO currentUser(Long userId);
}
