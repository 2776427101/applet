package com.banxing.classassistant.dto;

import lombok.Data;

/**
 * 首页通知（出参）
 *
 * <p>通知是「算出来的」，不是一张表。每次请求都从业务表里现算：
 * 待审批的请假、今天到期还没做的待办、今天过生日的学生。
 *
 * <p>为什么不建 t_notice 表：建表就要有"已读/未读"，而"已读"是一条
 * 需要人主动点击才成立的状态 —— 老师批完假之后，那条通知本来就不该
 * 再出现在列表里。用业务状态当通知状态，处理完自动消失，不需要维护
 * 一张会和真实业务脱节的消息表。
 *
 * <p>代价是没法做"历史通知"。首页这个框要的是"现在要你处理什么"，
 * 不是"过去发生过什么"，所以这个代价可以接受。
 */
@Data
public class HomeNoticeVO {

    /** 类型：leave=待审批请假 todo=今天到期的待办 birthday=今天生日 */
    private String kind;

    /** 主标题，一行放得下，首页那个框直接显示这一句 */
    private String title;

    /** 副标题，通知页里显示，首页不显示（框只有一行高） */
    private String desc;

    /** 时间文案，服务端拼好：刚刚 / 12 分钟前 / 今天 08:12 / 昨天 21:30 / 11-03 */
    private String atText;

    /** 同上的毫秒时间戳，前端要排序或自己格式化时用 */
    private long atMillis;

    /** 点击跳转用。哪个字段有值就说明是哪种通知 */
    private Long studentId;

    private Long leaveId;

    private Long todoId;

    /**
     * 这条通知属于哪个班。
     *
     * <p>v0.44 起通知默认跨班聚合（老师带两个班时，不用切班也能看到另一个班有人请假），
     * 所以每条都得带上班级名，否则列表里出现"王小明提交了请假申请"，
     * 老师不知道是哪个班的王小明。
     */
    private Long classId;

    private String className;
}
