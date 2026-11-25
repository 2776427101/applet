package com.banxing.classassistant.dto;

import com.banxing.classassistant.entity.Todo;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 一条待办（出参）
 * 只放前端要显示的字段，数据库里有什么不代表接口里就要给什么
 */
@Data
public class TodoVO {

    private Long id;

    private String title;

    // 0=未完成 1=已完成，前端按数字做样式
    private Integer done;

    // 截止时间，可为 null
    // 加 @JsonFormat 才会输出 "2026-10-25 18:00:00"，
    // 不加默认输出 "2026-10-25T18:00:00"，和契约对不上
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime dueAt;

    /**
     * 实体转 VO。
     * 写成静态工厂方法，Service 里一行 .map(TodoVO::from) 就转完了
     */
    public static TodoVO from(Todo t) {
        TodoVO vo = new TodoVO();
        vo.setId(t.getId());
        vo.setTitle(t.getTitle());
        vo.setDone(t.getDone());
        vo.setDueAt(t.getDueAt());
        return vo;
    }
}