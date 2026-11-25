package com.banxing.classassistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 接龙的一条参与记录，对应表 t_relay_entry。
 *
 * <p>{@code userId} 和 {@code studentId} 二选一：
 * <ul>
 *   <li>自助接龙：{@code userId} 有值 —— 学生自己点的那条</li>
 *   <li>老师代填：{@code studentId} 有值 —— 学生还没有账号时唯一能用的方式</li>
 * </ul>
 * 两个唯一索引分别防重：{@code (relay_id, user_id)} 和 {@code (relay_id, student_id)}。
 * MySQL 的唯一索引允许多个 NULL，所以"代填行的 user_id 全是 NULL"不会互相打架。
 *
 * <p>{@code nameSnapshot} 存提交时的姓名：名单是历史记录，
 * 不该因为有人后来改了名就跟着变。
 */
@Data
@TableName("t_relay_entry")
public class RelayEntry {

    /** 正常 */
    public static final int STATUS_NORMAL = 1;

    /** 已撤回 */
    public static final int STATUS_REMOVED = 0;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long relayId;

    /** 自助接龙的人；代填时为 null */
    private Long userId;

    /** 代填的学生；自助时为 null */
    private Long studentId;

    /** 序号，从 1 开始，按提交顺序 */
    private Integer seq;

    /** 填写内容；格式=序号 时为空串 */
    private String value;

    /** 提交时的姓名快照 */
    private String nameSnapshot;

    /** 1正常 0已撤回 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
