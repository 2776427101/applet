package com.banxing.classassistant.dto;

import lombok.Data;

/**
 * 加入前的预览信息。
 *
 * <p>这个接口<b>不抛「已过期」异常</b>，而是把 {@code expired} 作为字段返回。
 * 原因：用户输完码之后应该看到「这个容器叫什么、几个人、要不要密码」，
 * 过期只该是页面上的一个红字提示，不是一个弹完就没了、还得重新输码的 toast。
 */
@Data
public class JoinPreviewVO {

    private Long containerId;

    private String name;

    private String subjectName;

    private String className;

    private Integer memberCount;

    /** 需要输入加入密码 */
    private Boolean needPassword;

    /** 邀请码已过期（容器本身还在） */
    private Boolean expired;

    /** 我在这个容器里的角色：0=不是成员 1/2/3=已是成员 */
    private Integer myRole;

    /** 我在黑名单里（此时不能加入） */
    private Boolean blacklisted;
}
