package com.banxing.classassistant.service;

import com.banxing.classassistant.dto.LoginVO;
import com.banxing.classassistant.dto.ProfileUpdateDTO;

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

    /**
     * 修改当前登录用户的资料（姓名、手机号）。
     *
     * @param userId 拦截器解析出的用户 ID，不从入参里取 ——
     *               否则前端传谁的 id 就能改谁的名字
     * @param dto    新资料
     * @return 改完之后的用户信息（前端拿它直接刷新页面，省一次 GET）
     */
    LoginVO.UserVO updateProfile(Long userId, ProfileUpdateDTO dto);
}
