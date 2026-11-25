package com.banxing.classassistant.dto;

import lombok.Data;

/**
 * 私信会话列表的数据库行（不是给前端的 VO）。
 *
 * <p>一个 studentId 一行，由 {@code GROUP BY student_id} 聚合出来：
 * {@code lastId} 是这个人最近一条消息的 id，{@code unread} 是他发来但老师还没读的条数。
 *
 * <p>为什么要先聚合再回表：直接 {@code ORDER BY id DESC} 取一批消息再在 Java 里去重，
 * 只能覆盖"最近 N 条"，某个学生很久没聊就会从列表里消失——
 * 而他恰恰是最需要被看到的那一个。
 */
@Data
public class DirectThreadRow {

    private Long studentId;

    /** 最近一条消息的 id，用来回表取内容和时间 */
    private Long lastId;

    /** 学生发来的、老师还没读的条数 */
    private Integer unread;
}
