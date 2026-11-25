package com.banxing.classassistant.dto;

import lombok.Data;

/**
 * 上传成功后的返回。
 *
 * <p>{@code url} 是<b>相对路径</b>（如 {@code /files/container/1/202610/xxx.jpg}），
 * 不带域名。原因：域名只有部署时才知道，写死在库里的话换域名就全部失效；
 * 前端拼一次 {@code BASE_URL} 的域名部分即可。
 */
@Data
public class UploadVO {

    /** 相对访问路径，前端拼上域名后可直接给 image/video 用 */
    private String url;

    /** 原始文件名，消息气泡上展示 */
    private String name;

    /** 字节数 */
    private Long size;
}
