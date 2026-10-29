package com.banxing.classassistant.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 教师首页聚合数据
 * 一次返回首页所有数据，前端只发一个请求
 */
@Data
public class HomeOverviewVO {

    // 班级信息
    private ClassInfoVO classInfo;

    // 四个统计数字
    private StatsVO stats;

    // 学期信息（第几周由后端算）
    private TermVO term;

    // 每日金句
    private String quote;

    /**
     * 班级信息
     */
    @Data
    public static class ClassInfoVO {
        private Long id;
        private String name;
        private String grade;
        // 班主任姓名（取自 t_user.name）
        private String teacherName;
        // 职务，目前固定"班主任"，以后可能扩展成"副班主任"
        private String teacherTitle;
    }

    /**
     * 首页四个统计数字
     * 用 long 而不是 int：count 查询返回的是 Long，避免拆箱时的空指针
     */
    @Data
    public static class StatsVO {
        // 今日请假：今天开始且状态=已通过的记录数
        private long leaveToday;
        // 今日生日：生日月日=今天且在读的学生数
        private long birthdayToday;
        // 学生总数：本班在读学生数
        private long studentCount;
        // 待办未完成：当前老师 done=0 的待办数
        private long todoUndone;
    }

    /**
     * 学期信息
     */
    @Data
    public static class TermVO {
        // 第几周，后端算好给前端
        private int weekNo;
        // 学期第一周的周一
        private LocalDate termStartDate;
    }
}
