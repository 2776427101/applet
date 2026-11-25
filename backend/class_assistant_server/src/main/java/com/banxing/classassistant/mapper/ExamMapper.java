package com.banxing.classassistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.banxing.classassistant.entity.Exam;
import org.apache.ibatis.annotations.Mapper;

/**
 * 考试 Mapper
 * 只有单表 CRUD，继承 BaseMapper 就够了，不用写一行 SQL
 */
@Mapper
public interface ExamMapper extends BaseMapper<Exam> {
}
