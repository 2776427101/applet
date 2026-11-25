package com.banxing.classassistant.dto;

import lombok.Data;

import java.util.List;

/**
 * 学生端通知列表
 *
 * <p>老师端的通知是「要你处理的事」（待审批请假、到期待办），
 * 学生端反过来，是「已经有结果、要告诉你的事」（请假批了/驳了、班主任发消息了）。
 * 同样是"算出来的、不是一张表"，理由见 {@link HomeNoticeVO} 的注释。
 */
@Data
public class StudentNoticeListVO {

    private long total;

    private List<StudentNoticeVO> list;

    @Data
    public static class StudentNoticeVO {

        /** leave=请假审批结果 message=班主任私信 */
        private String kind;

        private String title;

        private String desc;

        /** 服务端拼好的时间文案：今天 08:12 / 昨天 21:30 / 11-03 */
        private String atText;

        private long atMillis;

        /** 请假结果指向的记录 id，点进去看详情 */
        private Long leaveId;

        /** 请假结果的状态 1=通过 2=驳回 3=销假，前端据此选颜色 */
        private Integer status;
    }
}
