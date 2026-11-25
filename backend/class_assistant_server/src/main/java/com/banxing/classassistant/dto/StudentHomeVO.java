package com.banxing.classassistant.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 学生端首页聚合数据
 *
 * <p>和老师端 {@link HomeOverviewVO} 是同构的：一个白卡（班级/班主任）+
 * 四个数字 + 学期周次，前端只发一个请求就能画完上半屏。
 *
 * <p><b>数字的含义完全换了。</b>老师看的是「全班今天几个人请假」，
 * 学生看的是「我自己这个月请了几次、还有几条没批」。同样是四宫格，
 * 一个在管人，一个在管自己 —— 这是两端最大的区别，也是不能复用
 * HomeOverviewVO 的原因。
 */
@Data
public class StudentHomeVO {

    private ClassInfoVO classInfo;

    private StatsVO stats;

    private TermVO term;

    /** 最近一次出成绩的考试。没考过试就是 null，前端要能画空态 */
    private LatestExamVO latestExam;

    /** 我的座位。还没排座就是 null */
    private SeatVO seat;

    @Data
    public static class ClassInfoVO {
        private Long id;
        private String name;
        private String grade;
        private String teacherName;
        private String teacherTitle;
    }

    @Data
    public static class StatsVO {
        /** 待审批：我提交了还没批的请假条数 */
        private long leavePending;
        /** 本月请假：本月开始过的请假条数（含已通过/已驳回） */
        private long leaveThisMonth;
        /** 班主任发来还没读的私信条数 */
        private long unread;
        /** 已经出成绩的考试次数 */
        private long examCount;
    }

    @Data
    public static class TermVO {
        private int weekNo;

        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate termStartDate;
    }

    @Data
    public static class LatestExamVO {
        private Long examId;
        private String name;

        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate examDate;

        /** 我的总分；一科都没录是 null，不是 0 */
        private BigDecimal total;

        /** 各科满分之和，用来画进度条 */
        private BigDecimal fullTotal;

        /** 班级排名，同分并列；没成绩是 null */
        private Integer rank;

        /** 参与排名的总人数（有成绩的人，不是全班人数） */
        private Integer classSize;
    }

    @Data
    public static class SeatVO {
        private Integer rowNo;
        private Integer colNo;
    }
}
