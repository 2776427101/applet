package com.banxing.classassistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 考试实体，对应表 t_exam（一次考试一条）
 */
@Data
@TableName("t_exam")
public class Exam {

    @TableId(type = IdType.AUTO)
    private Long id;

    // 所属班级
    private Long classId;

    // 考试名称，如 第一次月考
    private String name;

    // 1=周测 2=月考 3=期中 4=期末
    private Integer type;

    // 考试日期
    private LocalDate examDate;

    // 科目，逗号分隔，如 "语文,数学,英语"
    //
    // 为什么不单独建 t_subject 表：科目是考试的一个属性，
    // 而且每次考试科目都可能不同（周测只考数学），单独建表反而多一次 JOIN。
    // 读出时 split(",") 就是 List<String>，够用了。
    private String subjects;

    // 各科满分，JSON 字符串，如 {"语文":150,"数学":150}
    // 存成字符串而不是三个列：科目是可变的，加一科就得多一列，宽表的老问题
    private String fullScore;

    private LocalDateTime createdAt;
}
