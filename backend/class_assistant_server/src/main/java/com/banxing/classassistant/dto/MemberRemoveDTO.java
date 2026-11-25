package com.banxing.classassistant.dto;

import lombok.Data;

/**
 * 移除成员。
 *
 * <p>{@code blacklist} 是这次移除最关键的一个选择：
 * 老师踢人时经常是「先请出去，还允许他改过再来」，也可能「这个人不能再进来」。
 * 不传默认不拉黑 —— 破坏性更小的那个选项应该是不传时的默认值。
 *
 * <p><b>注意：这个 DTO 不由 Spring 绑定，也没有 {@code @Valid}。</b>
 * 移除接口是 DELETE，参数走 query（原因见 {@code ContainerController#remove}），
 * Controller 手工 new 出来再塞值。所以：
 * <ul>
 *   <li>这里的校验注解<b>不会生效</b>，别以为加了 {@code @Size} 就安全了；</li>
 *   <li>长度兜底在 {@code ContainerMemberServiceImpl#remove} 里用 {@code clip()} 做，
 *       超长截断而不是报错 —— 用户传了个长原因不该 500。</li>
 * </ul>
 * 留着这个 DTO 而不是把两个参数一路传进 Service，是为了和 mute/role 两个接口
 * 保持同一套方法签名，将来真要加字段（比如"是否通知家长"）只改一处。
 */
@Data
public class MemberRemoveDTO {

    /** true=同时加入黑名单，该用户不能再凭邀请码加入，直到群主解除 */
    private Boolean blacklist;

    /** 移除原因 / 拉黑原因，可空；超长会被 clip 到 100 字 */
    private String reason;
}
