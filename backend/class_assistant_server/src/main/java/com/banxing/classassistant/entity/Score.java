package com.banxing.classassistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 成绩实体，对应表 t_score（一行 = 某学生某科的成绩）
 *
 * <p>注意 score 的类型是 BigDecimal 而不是 double。
 * 数据库列是 DECIMAL(6,1)，MyBatis 读出来就是 BigDecimal，
 * 用 double 接会丢掉末尾的精度，而且 double 做求和会出现 0.1+0.2=0.30000000000000004。
 * 算平均分、总分一律用 BigDecimal，最后再四舍五入到 1 位小数。
 */
@Data
@TableName("t_score")
public class Score {

    @TableId(type = IdType.AUTO)
    private Long id;

    // 所属班级（冗余字段：统计时不用再 JOIN t_exam 拿 class_id）
    private Long classId;

    private Long examId;

    private Long studentId;

    // 科目名
    private String subject;

    // 分数。null = 缺考，不是 0 分。这个区别决定总分和及格率算不算这一科
    private BigDecimal score;

    // 满分，默认 100。有些科目 150，及格线是它的 60%
    private BigDecimal fullScore;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
