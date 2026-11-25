package com.banxing.classassistant.service;

import com.banxing.classassistant.dto.UploadVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 群聊附件上传。
 *
 * <p>只负责"存下来并返回一个能访问的路径"，不碰消息表 ——
 * 上传和发消息是两步：用户可能选了图又取消发送，那时文件已经传上来了，
 * 强行合成一个接口只会让"取消"变成删文件。
 */
public interface ContainerFileService {

    /**
     * 上传一个附件。
     *
     * @param userId      当前登录用户，必须是群成员
     * @param containerId 群组 id
     * @param file        文件本体
     * @param kind        image / video / file，决定扩展名白名单和大小上限
     */
    UploadVO upload(Long userId, Long containerId, MultipartFile file, String kind);

    /**
     * 学生上传附件。
     *
     * <p>和 {@link #upload} 的差别只有权限判断：学生不是 {@code t_user}，
     * 不在 {@code t_container_member} 里，{@code requireMember} 对他们永远是 false。
     * 所以可见性必须由调用方（{@code StudentGroupService}）先按
     * "本班的群 / 用邀请码加入的群"两条规则判完再进来。
     *
     * <p>落盘、扩展名白名单、大小上限完全共用 —— 学生能传的东西和老师一模一样，
     * 只有"能往哪个群传"不同。
     */
    UploadVO uploadForStudent(Long containerId, MultipartFile file, String kind);
}
