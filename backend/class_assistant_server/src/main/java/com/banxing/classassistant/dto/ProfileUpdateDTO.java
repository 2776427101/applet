package com.banxing.classassistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改个人资料的入参。
 *
 * <p><b>为什么不给 role / status / openid</b>：
 * 角色决定「能不能改成绩」，状态决定「能不能登录」，这两个字段一旦能被
 * 前端随便改，等于把权限系统交给用户自己填。openid 是登录凭据，更不能动。
 *
 * <p><b>为什么不给 avatar</b>：头像要先上传拿 URL，属于「文件 + 表单」两步，
 * 混进这个接口会让它多一个失败分支。等真的要做头像时单独加接口。
 *
 * <p>phone 允许空串（清空）—— 手机号不是必填项，
 * 老师不填也能正常用，只是同学联系不上他。
 */
@Data
public class ProfileUpdateDTO {

    @NotBlank(message = "姓名不能为空")
    @Size(max = 16, message = "姓名最多 16 个字")
    private String name;

    /**
     * 手机号。不传 = 不改；传空串 = 清空。
     *
     * <p>校验写成「要么空、要么 11 位手机号」而不是 {@code @NotBlank}：
     * 这个字段是选填的，强制必填会把「没填手机号」的老师挡在保存按钮外面。
     */
    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不对")
    private String phone;

    /**
     * 单位 / 学校。同样是选填，不传 = 不改，传空串 = 清空。
     *
     * <p>为什么不放在班级上：一个老师可以带多个班，但单位只有一个；
     * 而且学生也在 t_user 里，学生没有班级可填。这两个概念不能合并。
     */
    @Size(max = 64, message = "单位最多 64 个字")
    private String unit;
}
