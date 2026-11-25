package com.banxing.classassistant.dto;

import lombok.Data;

/**
 * 学生用邀请码加入群的结果
 *
 * <p>无论是不是"本来就在这个群"，都返回群信息 —— 前端不用为"已在群里"
 * 单独准备一套文案，直接进去就行，只是提示语不同。
 */
@Data
public class StudentGroupJoinVO {

    private StudentGroupVO group;

    /** true = 本来就能看见这个群（本班群，或者之前用码加过） */
    private boolean alreadyIn;
}
