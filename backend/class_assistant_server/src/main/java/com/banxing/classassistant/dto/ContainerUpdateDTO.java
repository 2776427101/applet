package com.banxing.classassistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改容器信息的入参。
 *
 * <p><b>全量提交</b>：{@code classId} 传 null 表示「解除与行政班的关联」。
 * 这和 {@link ClassSaveDTO} 的「传什么改什么」不同，原因是这里的表单页
 * 本来就把三个字段一起读出来、一起提交，做成部分更新反而要额外传
 * {@code clearClass} 之类的标记位，更容易出错。
 *
 * <p>邀请码、有效期、加入密码、状态都不在这里 —— 它们是独立接口 + 二次确认。
 */
@Data
public class ContainerUpdateDTO {

    @NotBlank(message = "学科名称不能为空")
    @Size(max = 32, message = "学科名称最多 32 个字")
    private String subjectName;

    @NotBlank(message = "容器名称不能为空")
    @Size(max = 64, message = "容器名称最多 64 个字")
    private String name;

    /** null = 不关联行政班 */
    private Long classId;
}
