package com.banxing.classassistant.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 创建学科容器的入参。
 *
 * <p>注意这里没有 {@code ownerId} / {@code inviteCode} / {@code status}：
 * 群主只能是当前登录老师，邀请码由后端生成，状态默认正常。
 * DTO 的字段列表就是权限边界 —— 前端夹带这三个字段会被 Jackson 直接丢弃
 * （和 ClassSaveDTO 不含 inviteCode 是同一个手法）。
 */
@Data
public class ContainerCreateDTO {

    /**
     * 群组名称。
     *
     * <p>历史上这里叫「学科名」，且前端另有一个「容器名称」字段。现在两者合并成一个：
     * 大学课程（高等数学（上））、考研课（考研英语冲刺）、跨年级选修都不是「学科」两个字
     * 能表达的，硬拆成两个字段只会逼老师把同一句话写两遍。
     * 字段名保留 {@code subjectName} 是为了不动已上线的接口契约和数据库列。
     */
    @NotBlank(message = "名称不能为空")
    @Size(max = 32, message = "名称最多 32 个字")
    private String subjectName;

    /** 不传则与 subjectName 相同（两者是同一个名字，留着是为了兼容老调用方） */
    @Size(max = 64, message = "名称最多 64 个字")
    private String name;

    /** 关联行政班，可空。传了就必须是当前老师自己带的班，否则 3002 */
    private Long classId;

    /** 邀请码有效天数；不传 = 永久有效 */
    @Min(value = 1, message = "有效期最少 1 天")
    @Max(value = 3650, message = "有效期最多 3650 天")
    private Integer inviteExpireDays;

    /**
     * 指定截止时间（精确到秒），优先级高于 {@link #inviteExpireDays}。
     *
     * <p>为什么要这个字段：老师经常说「本周五晚上 8 点前有效」，
     * 只给天数的话前端只能自己换算，跨零点/跨时区都会算错一天。
     * 由前端直接把日期+时间传上来，后端只做一次「不能早于现在」的校验。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime inviteExpireAt;

    /** 加入密码，可空；传了就按 6-20 位校验，后端只存哈希 */
    @Size(min = 6, max = 20, message = "加入密码需 6-20 位")
    private String joinPassword;
}
