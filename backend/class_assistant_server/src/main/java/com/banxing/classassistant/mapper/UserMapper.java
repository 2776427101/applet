package com.banxing.classassistant.mapper;

import com.banxing.classassistant.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
/**
 * 用户表数据访问接口
 * 继承 BaseMapper<User> 后，自动拥有按 ID 查询、条件查询、插入、更新等方法
 * 复杂 SQL 才需要在这里写自定义方法（配合 resources/mapper/UserMapper.xml）
 */
public interface UserMapper extends BaseMapper<User> {
}
