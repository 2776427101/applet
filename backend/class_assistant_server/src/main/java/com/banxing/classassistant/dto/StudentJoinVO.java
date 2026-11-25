package com.banxing.classassistant.dto;

import lombok.Data;

/**
 * 加入班级的结果
 *
 * <p><b>无论是不是"本来就在这个班"，都返回一张可用的 token。</b>
 * 返回 null 让前端判断的话，总会有人把 null 存进 storage，
 * 然后拿着空 token 去调接口，看到的是「登录失败」而不是「你已经在这个班了」。
 */
@Data
public class StudentJoinVO {

    /** 新班级的 token（role=2，classId 已换成目标班） */
    private String token;

    private long expiresIn;

    /** 加入之后的"我是谁"，字段和登录返回的 student 完全一样 */
    private StudentInfoVO student;

    /** true = 本来就在这个班，什么都没变（前端只弹一句提示，不用跳页） */
    private boolean alreadyIn;
}
