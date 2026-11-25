package com.banxing.classassistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 学生实体，对应表 t_student
 */
@Data
@TableName("t_student")
public class Student {

    // 主键，自增
    @TableId(type = IdType.AUTO)
    private Long id;

    // 所属班级 ID，所有查询都要带上它，否则会看到别的班的数据
    private Long classId;

    // 姓名
    private String name;

    // 性别：0=未知 1=男 2=女
    private Integer gender;

    // 生日，注意是 LocalDate（只有年月日，没有时分秒）
    private LocalDate birthday;

    // 学号
    private String studentNo;

    /* ---------- 以下 9 个字段全部可为 null，由 03_alter_student_fields.sql 增加 ---------- */

    // 宿舍，如「3栋502」
    private String dormitory;

    // 学生本人手机号。注意是 String 不是 Long ——
    // 手机号不做算术运算，用数字类型会吃掉前导 0，也会让 13800000000 这种值在某些序列化下变成科学计数法
    private String phone;

    // 微信号
    private String wechat;

    // 家人电话 1
    private String familyPhone1;

    // 家人电话 2
    private String familyPhone2;

    // 培训方向，如「软件开发」
    private String trainingDirection;

    // 民族，如「汉族」
    private String nation;

    // 政治面貌，如「共青团员」
    private String politicalStatus;

    // 入党积极分子。存原文而不是 0/1：
    // 名单里可能写「是」「否」「已递交申请」，统一成布尔会丢掉后半句
    private String partyActivist;

    // 状态：1=在读 0=已离班
    private Integer status;

    // 创建时间
    private LocalDateTime createdAt;

    // 更新时间
    private LocalDateTime updatedAt;
}
