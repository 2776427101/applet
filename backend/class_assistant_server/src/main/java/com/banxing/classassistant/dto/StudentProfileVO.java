package com.banxing.classassistant.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;

/**
 * 学生端「我的资料」（出参）
 *
 * <p>字段是 t_student 里<b>学生本人该看见的那些</b>。故意没有 partyActivist、
 * familyPhone2、status 之外的内部字段 —— 老师端能看到全部，是因为他要填表、
 * 要联系家长；学生看自己那份，不需要把整行原样吐出来。
 *
 * <p>也没有 classId / teacherId 这类外键：学生端所有接口的班级都从 token 来，
 * 前端多一个 id 就多一个可以乱传的参数。
 */
@Data
public class StudentProfileVO {

    private Long id;

    private String name;

    private String studentNo;

    /** 1=男 2=女 */
    private Integer gender;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate birthday;

    private String className;

    private String teacherName;

    private String dormitory;

    private String phone;

    private String wechat;

    /* 家庭电话（familyPhone1/2）和培养方向不在这里：家庭电话是家长的联系方式，
       学生本人看自己的档案不需要它；培养方向是教务口径，学生看了也改不了。
       老师端的学生详情仍然有这两项 —— 那是他填表、联系家长用的。 */
    private String nation;

    private String politicalStatus;
}
