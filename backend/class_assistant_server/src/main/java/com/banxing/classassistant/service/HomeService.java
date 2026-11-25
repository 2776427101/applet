package com.banxing.classassistant.service;

import com.banxing.classassistant.dto.HomeNoticeListVO;
import com.banxing.classassistant.dto.HomeOverviewVO;

/**
 * 教师首页业务
 */
public interface HomeService {

    /**
     * 查询首页聚合数据
     * @param userId 当前登录老师 ID（由 Controller 从拦截器塞的 request 属性里取）
     * @param classId 看哪个班；null = 我的第一个班（多班级下前端必须传）
     * @return 首页所需全部数据
     */
    HomeOverviewVO overview(Long userId, Long classId);

    /**
     * 首页通知：待审批请假 / 今天到期的待办 / 今天生日
     *
     * <p>不落库、不记已读。通知的生命周期跟着业务走：假批完了、待办做完了，
     * 对应那条通知就自然消失。
     *
     * @param userId  当前登录老师
     * @param classId 看哪个班；null = 第一个班
     * @param limit   最多返回几条，null 或 &lt;=0 时默认 20
     */
    HomeNoticeListVO notices(Long userId, Long classId, Integer limit);
}
