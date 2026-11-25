package com.banxing.classassistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学生"用邀请码加入非本班群"的记录，对应表 t_student_container。
 *
 * <p><b>本班的群不在这张表里。</b> 一个班的群（{@code t_subject_container.class_id}
 * 等于学生所在班）是自动可见的：花名册里有你，你就在群里。再存一条记录只是
 * 把花名册抄一遍，还要处理"转班了这条记录怎么算"。
 *
 * <p>这张表只为一种情况存在：学生想进<b>别的</b>群（跨班旁听、高数群这种
 * {@code class_id} 为空的公共群）。那种群没有"班级"可以自动判断，
 * 只能靠邀请码，也就只能靠这张表记住"他进来过"。
 */
@Data
@TableName("t_student_container")
public class StudentContainer {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** t_student.id */
    private Long studentId;

    /** t_subject_container.id */
    private Long containerId;

    /** 加入时所在的班，仅作展示；学生转班后这个值会过期，不要拿它做权限判断 */
    private Long classId;

    private LocalDateTime createdAt;
}
