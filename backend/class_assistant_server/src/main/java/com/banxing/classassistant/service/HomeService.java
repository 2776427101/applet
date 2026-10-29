package com.banxing.classassistant.service;

import com.banxing.classassistant.dto.HomeOverviewVO;

/**
 * 教师首页业务
 */
public interface HomeService {

    /**
     * 查询首页聚合数据
     * @param userId 当前登录老师 ID（由 Controller 从拦截器塞的 request 属性里取）
     * @return 首页所需全部数据
     */
    HomeOverviewVO overview(Long userId);
}
