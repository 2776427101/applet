package com.banxing.classassistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发一条私信。老师和学生共用。
 */
@Data
public class SendMessageDTO {

    @NotBlank(message = "消息不能为空")
    @Size(max = 500, message = "一条消息最多 500 字")
    private String content;
}
