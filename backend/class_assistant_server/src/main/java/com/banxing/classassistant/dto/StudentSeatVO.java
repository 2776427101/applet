package com.banxing.classassistant.dto;

import lombok.Data;

/**
 * 学生端「我的座位」
 *
 * <p>只给一个格子，不给整张座位图。整张图意味着把全班每个人的座位
 * 都发给每个学生 —— 老师端那是工作台，学生端那是别人的信息。
 * rows/cols 只用来画"第 N 排 / 共 M 排"这种相对位置，不泄露具体布局。
 */
@Data
public class StudentSeatVO {

    /** 第几排（纵向），1 开始 */
    private Integer rowNo;

    /** 第几列（横向），1 开始 */
    private Integer colNo;

    /** 教室一共几排，用来显示"3 / 7" */
    private Integer rows;

    /** 教室一共几列 */
    private Integer cols;

    /** 已经排座了没有。false 时 rowNo/colNo 都是 null */
    private boolean seated;
}
