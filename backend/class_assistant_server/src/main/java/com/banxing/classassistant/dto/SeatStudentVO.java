package com.banxing.classassistant.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 还没排座的学生。
 *
 * <p>单独一个类而不是复用 SeatVO：它没有 row/col，
 * 硬塞进 SeatVO 就会出现两个永远为 null 的字段，读代码的人还得猜是什么意思。
 */
@Data
@AllArgsConstructor
public class SeatStudentVO {

    private Long studentId;

    private String studentName;

    private Integer gender;
}
