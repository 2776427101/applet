package com.banxing.classassistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 教师待办实体，对应表 t_todo
 */
@Data
@TableName("t_todo")
public class Todo {

    // 主键，自增
    @TableId(type = IdType.AUTO)
    private Long id;

    // 所属老师 ID
    private Long teacherId;

    // 所属班级 ID
    private Long classId;

    // 待办内容
    private String title;

    // 是否完成：0=未完成 1=已完成
    private Integer done;

    // 截止时间
    private LocalDateTime dueAt;

    // 创建时间
    private LocalDateTime createdAt;

    // 更新时间
    private LocalDateTime updatedAt;
}
