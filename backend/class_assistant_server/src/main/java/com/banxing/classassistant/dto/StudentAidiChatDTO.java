package com.banxing.classassistant.dto;

import lombok.Data;

import java.util.List;

/**
 * 学生问乐迪
 *
 * <p>和老师端的 {@link AidiChatDTO} 比，<b>没有 classId</b>：
 * 学生在哪个班由 token 决定，前端说了不算。
 *
 * <p>也没有 action / 确认卡：学生端乐迪是只读的。
 * 能改数据的操作（请假、销假、改资料）都有明确入口，
 * 让模型代点反而多一层"它是不是点错了"的风险。
 */
@Data
public class StudentAidiChatDTO {

    /** 对话历史，最后一条必须是 role=user */
    private List<Msg> messages;

    @Data
    public static class Msg {
        /** 只认 user / assistant，其他一律丢弃 */
        private String role;
        private String content;
    }
}
