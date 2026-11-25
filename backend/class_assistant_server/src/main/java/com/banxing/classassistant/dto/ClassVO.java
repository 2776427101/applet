package com.banxing.classassistant.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;

/**
 * 班级信息（教师端「班级设置」页用）。
 *
 * <p>和首页 {@code HomeOverviewVO.ClassInfoVO} 的区别：那个是首页卡片用的精简版
 * （只要名字、年级、班主任姓名），这个是设置页用的完整版，
 * 多了学期开始日（决定「第几周」）和邀请码。
 *
 * <p>为什么不合并成一个：首页每开一次就查邀请码和学期开始日是浪费，
 * 而且两个接口的字段需求会越走越远（首页要统计，设置页要可编辑项）。
 * 共享一个 VO 的结果通常是两边都被迫带上对方要的字段。
 */
@Data
public class ClassVO {

    private Long id;

    /** 班级名，如「628班」「2028届软通1班」 */
    private String name;

    /** 年级，如「高一」 */
    private String grade;

    /**
     * 本学期第一周的周一。首页的「第几周」就是拿今天和它算的。
     * 改这个值会直接改变首页显示的周次，所以设置页要给出明确提示
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate termStartDate;

    /** 学生加入班级用的邀请码。只读 —— 改它会让已经发出去的码失效 */
    private String inviteCode;

    /** 在读学生数。设置页显示「本班 47 人」，改名前让老师确认改的是哪个班 */
    private Integer studentCount;

    /**
     * 这个班的班级群 id。通讯录里点「班级群」就是进这个群聊。
     * 为 null 表示这个班还没有群（历史数据，或建群时失败了），前端要给出「建群」入口
     */
    private Long containerId;

    /** 班级群名称。一般等于班级名，但群可以单独改名，所以不合并成一个字段 */
    private String containerName;

    /**
     * 班级群里的总人数 = 老师成员 + 本班在读学生。
     *
     * <p>不能只数 t_container_member：学生不是 t_user，不在那张表里。
     * 只数成员表会得到「1 人」，而学生端打开同一个群看到的是「59 人」——
     * 老师照着「1 人」判断"群里没人"，其实是全班都在。
     */
    private Integer groupTotalCount;

    /** 班级群里的老师成员数（不含学生）。通讯录里要区分「几个老师在看这个群」时用它 */
    private Integer groupMemberCount;
}
