package com.banxing.classassistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

/**
 * 确认导入的入参。
 *
 * <p>{@code rows} 就是预览时返回的那一份，前端不做任何加工直接回传。
 * 后端会<b>重新校验一遍</b>，因为：
 * <ul>
 *   <li>从预览到确认之间，别的老师可能刚加了一个同学号的学生</li>
 *   <li>前端传什么都不能信 —— 这是所有写接口的共同前提</li>
 * </ul>
 */
@Data
public class ImportCommitDTO {

    /**
     * 学号已存在时怎么办。
     *
     * <ul>
     *   <li>{@code skip}：跳过，库里那 5 个人一个字都不动</li>
     *   <li>{@code update}：用名单里的新信息覆盖姓名、性别、生日和 9 个扩展字段。
     *       <b>不会覆盖 status</b> —— 名单里没有「在读/离班」，不该动这个字段</li>
     * </ul>
     *
     * <p>没有第三种「新增一条重复的」：同班学号有唯一键，插进去直接报错，
     * 而且历史成绩、请假会分不清挂在哪一个身上。
     */
    @NotBlank(message = "请选择重复学生怎么处理")
    @Pattern(regexp = "skip|update", message = "重复学生只能选 skip（跳过）或 update（更新）")
    private String mode;

    @NotEmpty(message = "没有可导入的数据")
    private List<ImportRowDTO> rows;
}
