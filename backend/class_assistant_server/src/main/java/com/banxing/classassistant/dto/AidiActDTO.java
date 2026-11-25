package com.banxing.classassistant.dto;

import lombok.Data;

import java.util.Map;

/**
 * 老师点了「确认」，把这个动作交给后端执行
 */
@Data
public class AidiActDTO {

    private Long classId;

    private Action action;

    @Data
    public static class Action {
        private String type;
        private Map<String, Object> params;
    }
}
