package com.banxing.classassistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 抽签，对应表 t_lottery。
 *
 * <p>两种模式：
 * <ul>
 *   <li>{@code kind=1} 随机点名：每点一次抽 N 人，{@code round} +1，结果追加</li>
 *   <li>{@code kind=2} 随机分组：分 k 组，结果整体替换</li>
 * </ul>
 *
 * <p>{@code poolSnapshot} 是发布时的候选名单快照。不存快照的话，
 * 学生转班之后历史结果就对不上了 —— 记录里出现一个"现在不在本班"的人，
 * 而没有任何地方能解释他为什么在那。
 *
 * <p>{@code seed} 存下来是为了可复现：同一个种子重新跑能得到同样的结果，
 * 有人质疑"是不是内定"时，把种子和名单给他就能自己验。
 */
@Data
@TableName("t_lottery")
public class Lottery {

    /** 未开始 */
    public static final int STATUS_WAITING = 1;

    /** 已开奖 */
    public static final int STATUS_DRAWN = 2;

    /** 已取消 */
    public static final int STATUS_CANCELED = 3;

    /** 随机点名 */
    public static final int KIND_PICK = 1;

    /** 随机分组 */
    public static final int KIND_GROUP = 2;

    /** 名单来自当前班学生 */
    public static final int SOURCE_CLASS = 1;

    /** 名单由发起人手动勾选 */
    public static final int SOURCE_MANUAL = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long containerId;

    private Long classId;

    private Long creatorId;

    /** 1随机点名 2随机分组 */
    private Integer kind;

    /** 1当前班学生 2手动勾选 */
    private Integer source;

    /** 候选名单快照，JSON: [{id,name}] */
    private String poolSnapshot;

    /** 点名模式每次抽几人 */
    private Integer drawCount;

    /** 分组模式分几组 */
    private Integer groupCount;

    /** 1=已抽中的人不再进入下一轮 */
    private Integer dedupe;

    /** 1=群内公开 0=仅发起人可见 */
    private Integer publicResult;

    /** 1未开始 2已开奖 3已取消 */
    private Integer status;

    /** 已抽轮次 */
    private Integer round;

    /** 随机种子，可复现 */
    private Long seed;

    /** 乐观锁版本号，防两人同时点「开始抽」 */
    private Integer version;

    private LocalDateTime drawnAt;

    private LocalDateTime createdAt;
}
