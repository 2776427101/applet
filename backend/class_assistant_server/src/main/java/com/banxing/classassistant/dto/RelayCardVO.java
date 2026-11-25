package com.banxing.classassistant.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 接龙卡片（嵌在聊天消息里）。
 *
 * <p>字段刻意少：卡片高度有 320rpx 的上限，能放下的只有
 * 标题 / 状态 / 人数 / 截止 / 按钮。说明、选项、参与名单都在详情页。
 *
 * <p>{@code deadlineText} 是后端算好的展示串（如「11-08 18:00」）。
 * 不让前端自己格式化，是因为 iOS 的 {@code new Date('2026-11-08 18:00:00')}
 * 会返回 Invalid Date —— 这个坑在小程序上很难查。
 */
@Data
public class RelayCardVO {

    private Long id;

    private String title;

    /** 1进行中 2已截止 3已满 4已结束 5已取消 */
    private Integer status;

    private Integer entryCount;

    /** null = 不限人数 */
    private Integer maxCount;

    /** 1序号 2数字 3手机号 4短文本 5单选 */
    private Integer entryFormat;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime deadline;

    /** 展示用：「11-08 18:00」；无截止时间为空串 */
    private String deadlineText;

    /** 我是否已经参与 */
    private Boolean joined;

    /** 我的条目 id，没参与时为 null。前端据此显示「修改我的填写」 */
    private Long myEntryId;

    /** 我是不是发起人 */
    private Boolean mine;
}
