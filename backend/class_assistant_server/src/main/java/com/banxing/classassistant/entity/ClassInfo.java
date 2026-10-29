package com.banxing.classassistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 班级实体，对应表 t_class
 * 类名没叫 Class，因为 Class 是 Java 关键字，会和 java.lang.Class 撞车
 */
@Data
@TableName("t_class")
public class ClassInfo {

    // 主键，自增
    @TableId(type = IdType.AUTO)
    private Long id;

    // 班级名，如 628班
    private String name;

    // 年级，如 高一
    private String grade;

    // 班主任的用户 ID，查"我带的班"就靠这个字段
    private Long headTeacherId;

    // 本学期第一周的周一，用来算"第几周"
    private LocalDate termStartDate;

    // 学生加入班级用的邀请码
    private String inviteCode;

    // 状态：1=正常 0=已归档
    private Integer status;

    // 创建时间
    private LocalDateTime createdAt;

    // 更新时间
    private LocalDateTime updatedAt;
}
