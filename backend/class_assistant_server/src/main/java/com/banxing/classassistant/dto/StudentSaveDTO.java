package com.banxing.classassistant.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 新增 / 编辑学生的入参
 *
 * <p>两个接口共用同一个 DTO：契约 §6.4 明确 PUT 是<b>全量提交</b>，
 * 字段和 POST 完全一致。分成两个类只会多一份要同步维护的字段列表。
 *
 * <p>注意这里是 DTO 不是 Entity。用 {@code @RequestBody Student} 接收的话，
 * 前端能顺手把 {@code classId}、{@code status} 一起传进来 ——
 * 改个 classId 就能把学生塞进别的班。DTO 的字段列表就是权限边界。
 */
@Data
public class StudentSaveDTO {

    /**
     * 姓名。
     * {@code @NotBlank} 比 {@code @NotNull} 严格：它会把 "   " 这种全空格的也拦下来，
     * 正好对上契约里「去首尾空格后不能为空」
     */
    @NotBlank(message = "姓名不能为空")
    @Size(max = 32, message = "姓名最多 32 个字")
    private String name;

    /**
     * 性别：0=未知 1=男 2=女。
     * 不传按 0 处理（契约 §6.3）。@Min/@Max 比手写 if 好，
     * 错误信息还能直接写给人看
     */
    @Min(value = 0, message = "性别只能是 0（未知）/ 1（男）/ 2（女）")
    @Max(value = 2, message = "性别只能是 0（未知）/ 1（男）/ 2（女）")
    private Integer gender;

    /**
     * 生日，可选。
     * {@code @PastOrPresent} 允许 null（可选字段），也允许今天出生 ——
     * 虽然一个班里有今天出生的学生不太现实，但这不是接口该管的
     */
    @PastOrPresent(message = "生日不能是未来日期")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate birthday;

    /**
     * 学号，可选。
     * 唯一性（同班内不重复）在这里校验不了 —— 要查库，
     * 放到 Service 里，冲突时抛 1006 而不是 1001
     */
    @Size(max = 32, message = "学号最多 32 个字")
    private String studentNo;

    /* ---------- 以下 9 个字段全部可选，不传 = 清空 ---------- */

    /**
     * 这些字段只做长度校验，不做格式校验。
     *
     * <p>为什么不给 phone 加 {@code @Pattern(regexp = "^1[3-9]\\d{9}$")}：
     * 家长电话可能是座机（0731-88888888）、可能是外地号码、可能带 +86，
     * 校规也没规定必须填手机。格式校验太严只会让老师「明明有号码却存不进去」。
     * 长度够防住脏数据就行。
     */

    @Size(max = 32, message = "宿舍最多 32 个字")
    private String dormitory;

    @Size(max = 20, message = "手机号最多 20 个字")
    private String phone;

    @Size(max = 64, message = "微信号最多 64 个字")
    private String wechat;

    @Size(max = 20, message = "家人电话 1 最多 20 个字")
    private String familyPhone1;

    @Size(max = 20, message = "家人电话 2 最多 20 个字")
    private String familyPhone2;

    @Size(max = 64, message = "培训方向最多 64 个字")
    private String trainingDirection;

    @Size(max = 32, message = "民族最多 32 个字")
    private String nation;

    @Size(max = 32, message = "政治面貌最多 32 个字")
    private String politicalStatus;

    @Size(max = 32, message = "入党积极分子最多 32 个字")
    private String partyActivist;
}
