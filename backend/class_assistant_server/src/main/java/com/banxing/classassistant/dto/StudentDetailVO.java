package com.banxing.classassistant.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 学生详情（返回给前端）
 *
 * <p>比列表项多带三块聚合信息：坐哪、请假多少次、最近一次考得怎么样。
 * 这些数据前端自己也能拼（调座位图 + 请假列表 + 成绩列表），但要发 3 个请求、
 * 还要在 47 条里翻人，任何一个失败整页就空着。详情页只服务一个人，
 * 一次查完更稳，也更快。
 *
 * <p>注意：这三块都可能为 null —— 没排座、没请过假、没考过试都是正常状态，
 * 不是错误。前端要按 null 显示「未排座」而不是显示 0 分。
 */
@Data
public class StudentDetailVO {

    /* ---------- 基本信息，字段与列表项一致 ---------- */

    private Long id;

    private String name;

    // 0=未知 1=男 2=女
    private Integer gender;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate birthday;

    private String studentNo;

    /* ---------- 9 个可空字段，前端按「联系方式 / 住宿与其他」两组展示 ---------- */

    // 宿舍
    private String dormitory;

    // 学生本人手机号
    private String phone;

    // 微信号
    private String wechat;

    // 家人电话 1 / 2
    private String familyPhone1;
    private String familyPhone2;

    // 培训方向
    private String trainingDirection;

    // 民族
    private String nation;

    // 政治面貌
    private String politicalStatus;

    // 入党积极分子
    private String partyActivist;

    // 1=在读 0=已离班
    private Integer status;

    // 入班时间，详情页显示「2026-09-01 入班」
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /* ---------- 三块聚合 ---------- */

    // 座位，null = 还没排座
    private SeatPos seat;

    // 请假统计，null = 一次都没请过
    private LeaveStat leave;

    // 最近一次考试，null = 这个班还没录过成绩
    private LatestScore latestScore;

    /**
     * 座位位置。只给行列号，不给座位 id ——
     * 前端要说的是「第 3 排第 5 列」，座位 id 对它没意义。
     */
    @Data
    public static class SeatPos {
        private Integer row;
        private Integer col;
    }

    /**
     * 请假统计
     *
     * <p>total 只算「已通过」和「已销假」，不含已驳回 ——
     * 老师看这个数字是想知道「这孩子缺了多少课」，
     * 被驳回的申请没缺课，算进去会虚高。
     */
    @Data
    public static class LeaveStat {
        private int total;
        private int pending;

        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate lastDate;

        // 最近一次的请假类型：1=病假 2=事假 3=其他
        private Integer lastType;
    }

    /**
     * 最近一次考试的成绩
     *
     * <p>用 List 而不是 Map 装各科：前端要按考试设定的科目顺序画条形图，
     * Map 的顺序由 JSON 序列化决定，靠不住。
     */
    @Data
    public static class LatestScore {
        private Long examId;
        private String examName;

        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate examDate;

        private List<SubjectScore> subjects;

        // 已录科目的总分，缺考的不计入
        private BigDecimal total;

        // 班内排名，同分并列
        private Integer rank;

        // 班级人数，用来显示「第 12 / 47 名」
        private Integer classSize;
    }

    @Data
    public static class SubjectScore {
        private String subject;

        // null = 缺考，不是 0 分
        private BigDecimal score;

        // 满分，前端画进度条用
        private BigDecimal fullScore;
    }
}
