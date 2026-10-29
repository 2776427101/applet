package com.banxing.classassistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体，对应表 t_user
 * 老师和学生共用一张表，靠 role 字段区分
 */
@Data
@TableName("t_user")   // 告诉 MyBatis-Plus 这个类对应哪张表
public class User {

    // 主键，数据库自增（IdType.AUTO 表示交给 MySQL 的 AUTO_INCREMENT）
    @TableId(type = IdType.AUTO)
    private Long id;

    // 微信 openid，登录唯一标识，查用户就靠它
    private String openid;

    // 微信 unionid，同一主体下多应用打通时用，一般为空
    private String unionid;

    // 角色：1=老师 2=学生
    private Integer role;

    // 姓名
    private String name;

    // 手机号
    private String phone;

    // 头像 URL
    private String avatar;

    // 状态：1=正常 0=禁用
    private Integer status;

    // 最后登录时间，每次登录后更新
    private LocalDateTime lastLoginAt;

    // 创建时间，由数据库 DEFAULT CURRENT_TIMESTAMP 维护，代码里一般不用赋值
    private LocalDateTime createdAt;

    // 更新时间，由数据库 ON UPDATE 自动维护
    private LocalDateTime updatedAt;
}
