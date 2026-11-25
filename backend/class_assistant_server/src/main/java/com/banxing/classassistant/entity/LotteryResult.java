package com.banxing.classassistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 抽签结果，对应表 t_lottery_result。
 *
 * <p>{@code studentName} 是快照，不是 join 出来的 ——
 * 学生改名或转班之后，历史记录里的名字要保持当时的样子。
 */
@Data
@TableName("t_lottery_result")
public class LotteryResult {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long lotteryId;

    /** 第几轮。点名模式每抽一次一轮；分组模式固定 1 轮 */
    private Integer round;

    /** 手动名单里的人可能不是本班学生，允许为空 */
    private Long studentId;

    private String studentName;

    /** 分组模式第几组；点名模式为 null */
    private Integer groupNo;

    /**
     * 同一轮内的顺序。
     *
     * <p>列名不能叫 {@code rank}：MySQL 8 把 RANK 当窗口函数保留字，
     * MyBatis-Plus 生成列清单时不加反引号，SQL 会在 {@code rank} 处直接语法报错
     * （报错信息指向 FROM，很容易误判成表名有问题）。
     */
    private Integer sortNo;
}
