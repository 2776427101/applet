package com.banxing.classassistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 座位实体，对应表 t_seat
 *
 * <p>一行 = 教室里的一格，不是"一个学生"。
 * 空座位也有一行，只是 student_id 为 null —— 这样 row_no / col_no 的最大值
 * 才等于真实的排数和列数。只存有人的格子的话，最后一排没人时排数会凭空少一排。
 */
@Data
@TableName("t_seat")
public class Seat {

    @TableId(type = IdType.AUTO)
    private Long id;

    // 所属班级，查询必须带上，否则会看到别的班的座位
    private Long classId;

    // 第几排，从 1 开始，1 排最靠近讲台
    private Integer rowNo;

    // 第几列，从 1 开始，按"站在讲台面向学生"的方向从左往右数
    private Integer colNo;

    // 学生 ID，null = 这个位置空着
    private Long studentId;

    private LocalDateTime createdAt;
}
