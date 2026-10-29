package com.banxing.classassistant.dto;

import lombok.Data;

import java.util.List;

/**
 * 导入预览。老师在上传文件后、真正写库前看到的全部信息。
 *
 * <p>为什么要有预览这一步：导入是唯一一个「一次改几十条数据」的操作。
 * 表头认错一列、行里混进别的班的人，写进去之后要一条条改回来。
 * 让老师先看一眼再确认，比事后补救便宜得多。
 */
@Data
public class ImportPreviewVO {

    /** 读的是哪个工作表 */
    private String sheetName;

    /** 表头在 Excel 第几行。老师说「我表头在第 2 行」时能对上 */
    private Integer headerRowNo;

    /** 数据行数（不含表头和空行） */
    private Integer dataRows;

    /** 跳过的空行数 */
    private Integer blankRows;

    /** 识别到的列，按列从左到右排 */
    private List<ColumnVO> columns;

    /**
     * 没认出来的列名，如「序号」「班主任」。
     *
     * <p>不报错、不拦截，只是告诉老师「这几列没用上」。
     * 名单里常有「序号」「备注」这种列，为它们中断导入没有意义。
     */
    private List<String> unmappedHeaders;

    private StatsVO stats;

    private List<ImportRowDTO> rows;

    /**
     * 本班的班级名（库里存的那个）。
     *
     * <p>单独返回它，是因为「班级不是本班」这个错误最需要的信息是
     * 「本班到底叫什么」。不给的话老师只能看到 69 行全红，
     * 不知道该改文件还是该改班名 —— 而班名在小程序里能改。
     */
    private String className;

    /**
     * 名单「班级」列里出现过的、和本班对不上的名字（去重，最多 5 个）。
     *
     * <p>只用于提示，不参与校验。老师看到「名单里写的是 628、629、630」，
     * 就能立刻判断是「文件里混了别的班」还是「班名起错了」。
     */
    private List<String> otherClassNames;

    /** 一行数据对应一列。前端画「表头映射」那一排标签用 */
    @Data
    public static class ColumnVO {
        /** 第几列，0 开始 */
        private Integer column;

        /** Excel 里的原始表头文字 */
        private String header;

        /** 识别成了哪个字段，没认出来是 null */
        private String field;

        /** 字段的中文名，如「学号」 */
        private String fieldLabel;

        /** 是否识别成功。前端用它决定标签是绿的还是灰的 */
        private Boolean mapped;
    }

    /**
     * 分类计数。
     *
     * <p>分成 create / duplicate / error 三类而不是「成功/失败」两类：
     * 「学号已存在」不是错误，是一个需要老师做决定的分支 ——
     * 更新他，还是跳过？这两件事的按钮不一样。
     */
    @Data
    public static class StatsVO {
        /** 能导入的总行数 = create + duplicate */
        private int importable;
        /** 会新增的行数 */
        private int create;
        /** 学号已存在、等老师决定更新还是跳过的行数 */
        private int duplicate;
        /** 有问题、怎么都导不进去的行数 */
        private int error;
        /** 名单里没有学号的行数 —— 这些人无法判断是否重复，会全部新增 */
        private int noStudentNo;
    }
}
