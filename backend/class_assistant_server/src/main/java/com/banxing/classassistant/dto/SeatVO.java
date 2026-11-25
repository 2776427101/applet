package com.banxing.classassistant.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 座位图里的一格。
 *
 * <p>空座位也返回，只是 studentId / studentName / gender 都是 null ——
 * 前端拿到 rows × cols 个格子，直接按 row/col 往格子里填，
 * 不用自己补空位，也不会出现"某个位置没返回所以画错行"。
 */
@Data
@AllArgsConstructor
public class SeatVO {

    // 第几排，从 1 开始。前端不要减 1
    private Integer row;

    // 第几列，从 1 开始
    private Integer col;

    // 学生 ID，空座位为 null
    private Long studentId;

    // 姓名，空座位为 null
    private String studentName;

    // 性别 1=男 2=女，空座位为 null。前端用它给座位卡上色
    private Integer gender;
}
