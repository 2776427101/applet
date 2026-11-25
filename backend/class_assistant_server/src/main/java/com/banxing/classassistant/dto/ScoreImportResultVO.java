package com.banxing.classassistant.dto;

import lombok.Data;

/**
 * 成绩导入的实际结果。
 *
 * <p>和学生名单导入（{@link ImportResultVO}）不同，这里<b>没有逐行失败列表</b>。
 * 原因：成绩写入是一条 {@code INSERT ... ON DUPLICATE KEY UPDATE} 批量语句，
 * 要么全成要么全败，不存在「前 20 个存了、第 21 个失败」的中间状态。
 * 所以写库失败时直接抛业务异常，用一句话说清原因，
 * 而不是给一个只有一行内容的 failures 数组让前端去渲染。
 */
@Data
public class ScoreImportResultVO {

    /** 写入的总格数 */
    private int saved;

    /** 涉及的学生数（不是行数：表里同一个人出现两行只算一个） */
    private int students;

    /** 新填的格数 */
    private int create;

    /** 被覆盖的格数 */
    private int overwrite;

    /** 被清成缺考的格数 */
    private int clear;
}
