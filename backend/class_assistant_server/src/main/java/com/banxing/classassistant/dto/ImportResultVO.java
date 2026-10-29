package com.banxing.classassistant.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 导入结果。
 *
 * <p>四个数字加起来应该等于提交的行数 —— 前端可以拿这个当自检。
 * 少于提交行数说明有行被静默丢了，那是 bug。
 */
@Data
public class ImportResultVO {

    /** 新插入的人数 */
    private int created;

    /** 覆盖更新的人数（只有 mode=update 才可能 > 0） */
    private int updated;

    /** 因为学号已存在而跳过的人数（只有 mode=skip 才可能 > 0） */
    private int skipped;

    /** 写库失败的人数。正常情况下应该是 0 */
    private int failed;

    /**
     * 失败明细。
     *
     * <p>不返回「成功明细」：69 行成功不需要看 69 条。
     * 但失败的每一行都要说清楚是哪一行、为什么 ——
     * 老师要拿着这个去 Excel 里改。
     */
    private List<Failure> failures = new ArrayList<>();

    @Data
    public static class Failure {
        private Integer excelRowNo;
        private String name;
        private String reason;

        public Failure(Integer excelRowNo, String name, String reason) {
            this.excelRowNo = excelRowNo;
            this.name = name;
            this.reason = reason;
        }
    }

    public void addFailure(Integer excelRowNo, String name, String reason) {
        this.failures.add(new Failure(excelRowNo, name, reason));
        this.failed++;
    }
}
