package com.banxing.classassistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 接龙，对应表 t_relay。
 *
 * <p>挂在<b>群</b>上而不是班级上：同一个班可能既有班级群又有课程群，
 * 接龙属于"某个群"。{@code classId} 只是冗余，用来拉代填的学生名单。
 *
 * <p>{@code status} 只存三种：进行中 / 已结束 / 已取消。
 * <b>「已满」和「已截止」不落库</b>，由 {@code RelayServiceImpl.effectiveStatus} 现算 ——
 * 落库就要有定时任务去改状态，而没有定时任务时数据库里会躺着一堆
 * "看起来还在进行、其实早过期"的接龙。
 */
@Data
@TableName("t_relay")
public class Relay {

    /** 进行中 */
    public static final int STATUS_ONGOING = 1;

    /** 发起人手动结束 */
    public static final int STATUS_CLOSED = 4;

    /** 发起人取消 */
    public static final int STATUS_CANCELED = 5;

    /** 已满。算出来的，只出现在出参里，不会写进库 */
    public static final int VIEW_FULL = 3;

    /** 已截止。同上 */
    public static final int VIEW_EXPIRED = 2;

    /** 仅序号，一键接龙 */
    public static final int FORMAT_SEQ = 1;
    /** 数字 */
    public static final int FORMAT_NUMBER = 2;
    /** 手机号 */
    public static final int FORMAT_PHONE = 3;
    /** 短文本 */
    public static final int FORMAT_TEXT = 4;
    /** 单选 */
    public static final int FORMAT_CHOICE = 5;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long containerId;

    /** 冗余的班级 id，课程群可能为 null */
    private Long classId;

    private Long creatorId;

    /** 接龙主题，≤ 30 字 */
    private String title;

    /** 说明，纯文本，可为空串 */
    private String description;

    /** 1序号 2数字 3手机号 4短文本 5单选 */
    private Integer entryFormat;

    /** 格式=数字 时的最小值 */
    private Integer formatMin;

    /** 格式=数字 时的最大值 */
    private Integer formatMax;

    /** 格式=单选 时的选项，JSON 数组字符串 */
    private String options;

    /** 人数上限，null = 不限 */
    private Integer maxCount;

    /** 截止时间，null = 不限 */
    private LocalDateTime deadline;

    /** 参与者截止前能否改自己的条目 */
    private Integer allowSelfEdit;

    /** 1进行中 4已结束 5已取消 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
