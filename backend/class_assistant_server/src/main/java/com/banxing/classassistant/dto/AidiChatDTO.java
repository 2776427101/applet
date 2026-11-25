package com.banxing.classassistant.dto;

import lombok.Data;

import java.util.List;

/**
 * 问乐迪
 *
 * <p><b>为什么把历史消息也传上来</b>：后端不存对话。存了就要建表、要清理、
 * 还要处理"老师换了手机看不到历史"这类问题。这一版是「问一句答一句」，
 * 上下文由前端带着走，页面关掉就结束 —— 简单，且不产生任何脏数据。
 */
@Data
public class AidiChatDTO {

    /** 问哪个班；null = 我的第一个班（和其他教师端接口一致） */
    private Long classId;

    /** 对话历史，最后一条必须是 role=user */
    private List<Msg> messages;

    @Data
    public static class Msg {
        /** 只认 user / assistant，其他一律丢弃 */
        private String role;
        private String content;
    }
}
