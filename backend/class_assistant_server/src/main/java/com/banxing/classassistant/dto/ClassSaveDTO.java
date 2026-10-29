package com.banxing.classassistant.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 修改班级信息的入参。
 *
 * <p><b>为什么不给 inviteCode</b>：邀请码是发出去给学生用的，
 * 老师随手改一次，已经加过班的学生就再也进不来了。要重置邀请码应该是
 * 一个独立的、带二次确认的操作，不该混在「改班级名」的表单里。
 *
 * <p><b>为什么不给 status</b>：归档班级是危险操作，同理。
 *
 * <p><b>为什么 grade / termStartDate 都是可空的</b>：
 * 这个接口是「传什么改什么」，不是「全量覆盖」。
 * 起因是名单导入页要提供一个「把班名改成名单里那个」的快捷操作 ——
 * 那个场景下前端只关心班名，如果还要求它把年级和开学日期一起传回来，
 * 就变成「先 GET 再 PUT」两次请求，中间任何一步失败都会让用户白点一次，
 * 而且一旦 GET 回来的是 null，PUT 上去就把开学日期清空了。
 *
 * <p>不传 = 不改。传了空串（grade）= 清空，这是有意义的操作，允许。
 * termStartDate 例外：数据库是 NOT NULL，传 null 就是不传。
 */
@Data
public class ClassSaveDTO {

    @NotBlank(message = "班级名称不能为空")
    @Size(max = 32, message = "班级名称最多 32 个字")
    private String name;

    /** 不传 = 不改；传空串 = 清空年级（年级本来就可以没有） */
    @Size(max = 16, message = "年级最多 16 个字")
    private String grade;

    /** 不传 = 不改。数据库 NOT NULL，所以这里没有「清空」这个语义 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate termStartDate;
}
