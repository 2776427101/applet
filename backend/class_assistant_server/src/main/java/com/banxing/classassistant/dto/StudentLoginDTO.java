package com.banxing.classassistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 学生登录：学号 + 姓名
 *
 * <p><b>v0.41：登录不再依赖花名册。</b>原来按学号去 t_student 里找，
 * 找不到就报「没找到学号 X」—— 这意味着<b>只有已经被班主任导入名单的人才能进来</b>。
 * 转专业、插班、名单还没更新的学生，全被挡在门外，而他连"进不去"的原因都看不到。
 *
 * <p>现在：查得到就照旧进班；查不到也发一张 token，只是这张 token 没有班级，
 * 只能用来<b>凭邀请码入班</b>（见 {@link com.banxing.classassistant.util.JwtUtil#createGuestStudentToken}）。
 *
 * <p>为什么不加密码：学生是小程序的一次性用户，记不住密码，忘了还要老师重置。
 * 学号 + 姓名是这套系统能承受的强度，真正的门是班级邀请码。
 */
@Data
public class StudentLoginDTO {

    /** 学号 */
    @NotBlank(message = "请填学号")
    @Size(max = 32, message = "学号不对")
    private String studentNo;

    /**
     * 姓名。<b>v0.41 起必填。</b>
     *
     * <p>原来是选填（填了才核对）。改成必填是因为登录不再依赖花名册：
     * 花名册里查不到这个人时，我们手里只剩"学号 + 姓名"两样东西，
     * 姓名是唯一能写进新建花名册行的内容 —— 没有它，班主任的名单里会出现
     * 一个只有学号、没有名字的人，谁也不知道那是谁。
     *
     * <p>顺带也把"输错学号登进别人账号"这道闸关紧了：花名册里有这个人时，
     * 姓名必须对得上才放行。
     */
    @NotBlank(message = "请填姓名")
    @Size(max = 32, message = "姓名太长")
    private String name;

    /**
     * 学号在多个班里时，用它指定进哪个班。
     * 第一次不传，后端返回班级列表，前端选完再带着它请求一次。
     */
    private Long classId;

    /**
     * 旧版客户端还会传邀请码。**保留字段只为不报 400，登录逻辑完全不看它。**
     * 删掉的话，没升级的小程序会直接收到参数校验失败，用户看到的是"登录坏了"。
     */
    @Size(max = 16, message = "邀请码不对")
    private String inviteCode;
}
