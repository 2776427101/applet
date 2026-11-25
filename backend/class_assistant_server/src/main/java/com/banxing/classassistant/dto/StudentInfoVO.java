package com.banxing.classassistant.dto;

import lombok.Data;

/**
 * 学生自己的信息（学生端首页用）
 */
@Data
public class StudentInfoVO {

    private Long id;

    private String name;

    private String studentNo;

    /** 0=未知 1=男 2=女 */
    private Integer gender;

    private Long classId;

    private String className;

    /** 班主任姓名，学生端显示"与 XX 老师" */
    private String teacherName;

    /** 还没读的私信条数，首页小红点 */
    private Integer unread;
}
