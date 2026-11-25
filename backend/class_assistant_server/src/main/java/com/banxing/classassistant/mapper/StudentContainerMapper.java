package com.banxing.classassistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.banxing.classassistant.entity.StudentContainer;

/**
 * 学生加入非本班群的记录
 *
 * <p>没有自定义 SQL：查"我加入过哪些群"和"我在不在这个群"都是一行 wrapper。
 */
public interface StudentContainerMapper extends BaseMapper<StudentContainer> {
}
