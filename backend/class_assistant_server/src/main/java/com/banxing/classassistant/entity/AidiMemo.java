package com.banxing.classassistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 乐迪的长期记忆，对应表 t_aidi_memo
 *
 * <p>一行 = 一条稳定事实（「周三下午没课」），不是一条对话。
 */
@Data
@TableName("t_aidi_memo")
public class AidiMemo {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属老师 */
    private Long userId;

    /** 所属班级；0 = 跨班通用 */
    private Long classId;

    /** 一条事实，一句话说清 */
    private String content;

    /** 1=有效 0=已忘掉 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
