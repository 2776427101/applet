package com.banxing.classassistant.dto;

import lombok.Data;

/**
 * 接龙里的一条记录（详情页列表用）。
 *
 * <p>{@code name} 是提交时的快照，不是现查的 —— 有人后来改了名，
 * 名单里那一行不该跟着变，否则老师会以为记录被改过。
 */
@Data
public class RelayEntryVO {

    private Long id;

    private Integer seq;

    /** 填写内容；格式=序号 时为空串 */
    private String value;

    private String name;

    /** 代填的学生 id，自助接龙为 null */
    private Long studentId;

    /** 是不是我填的那条 */
    private Boolean mine;
}
