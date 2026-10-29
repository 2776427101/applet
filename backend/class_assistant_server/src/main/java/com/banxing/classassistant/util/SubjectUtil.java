package com.banxing.classassistant.util;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 科目相关的转换工具。
 *
 * <p>考试表里科目存成 "语文,数学,英语"，满分存成 {"语文":150} 这样的 JSON。
 * 考试模块和成绩模块都要做这两种转换，所以抽出来，
 * 免得两个 ServiceImpl 各写一份 split(",")。
 */
public final class SubjectUtil {

    /** ObjectMapper 配置好之后是线程安全的，可以做成静态常量复用 */
    private static final ObjectMapper JSON = new ObjectMapper();

    private SubjectUtil() {
    }

    /** "语文,数学,英语" → ["语文","数学","英语"]；空值返回空列表，不返回 null */
    public static List<String> split(String s) {
        if (s == null || s.isBlank()) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>();
        for (String part : s.split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    /**
     * 读出各科满分。数据库里没存（null）或者 JSON 坏了，就统一按 100。
     *
     * <p>为什么坏了不抛异常：满分只影响及格线的显示，
     * 因为一个显示字段让整个成绩页 500 不值得。
     */
    public static Map<String, BigDecimal> parseFullScore(String json, List<String> subjects) {
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        for (String s : subjects) {
            map.put(s, new BigDecimal("100"));
        }
        if (json == null || json.isBlank()) {
            return map;
        }
        try {
            Map<String, Integer> raw = JSON.readValue(json, new TypeReference<Map<String, Integer>>() {
            });
            raw.forEach((k, v) -> {
                if (map.containsKey(k) && v != null && v > 0) {
                    map.put(k, new BigDecimal(v));
                }
            });
        } catch (Exception ignore) {
            // 用默认值
        }
        return map;
    }

    /** 把 {"语文":150} 写成 JSON 字符串；没有有效值时返回 null（读的时候默认全 100） */
    public static String writeFullScore(List<String> subjects, Map<String, Integer> fullScore) {
        if (fullScore == null || fullScore.isEmpty()) {
            return null;
        }
        Map<String, Integer> clean = new LinkedHashMap<>();
        for (String s : subjects) {
            Integer v = fullScore.get(s);
            if (v != null && v > 0) {
                clean.put(s, v);
            }
        }
        if (clean.isEmpty()) {
            return null;
        }
        try {
            return JSON.writeValueAsString(clean);
        } catch (Exception e) {
            return null;
        }
    }
}
