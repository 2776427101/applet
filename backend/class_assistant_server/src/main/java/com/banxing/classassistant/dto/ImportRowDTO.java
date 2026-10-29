package com.banxing.classassistant.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 导入过程中的一行。预览返回给前端，前端确认后原样传回来提交。
 *
 * <p>为什么预览和提交共用一个类：老师看到的每一行，和最终写进库的每一行，
 * 必须是同一份数据。分成两个类就要写两遍映射，改一处漏一处就会出现
 * 「预览显示有手机号、存进去是空」这种最难查的问题。
 *
 * <p><b>提交时 {@code action} / {@code reason} / {@code errors} 会被忽略</b>：
 * 那是预览的判定结果，提交时后端会<b>重新算一遍</b>。
 * 前端传 {@code action: "create"} 但这个人其实已经存在？照样按重复处理。
 * 校验结果永远不信客户端。
 */
@Data
public class ImportRowDTO {

    /** 在 Excel 里的行号（1 开始）。报错时用它，老师能直接定位 */
    private Integer excelRowNo;

    private String name;
    private String studentNo;

    /** 0=未知 1=男 2=女 */
    private Integer gender;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate birthday;

    private String dormitory;
    private String phone;
    private String wechat;
    private String familyPhone1;
    private String familyPhone2;
    private String trainingDirection;
    private String nation;
    private String politicalStatus;
    private String partyActivist;

    /* ---------- 下面三个只有预览会填，提交时忽略 ---------- */

    /** create=新增，duplicate=学号已存在，error=这行有问题不能导 */
    private String action;

    /** 一句话说明，如「学号 20280001 已被 李四 使用」 */
    private String reason;

    /** 这一行的所有问题。errors 非空时 action 一定是 error */
    private List<String> errors;
}
