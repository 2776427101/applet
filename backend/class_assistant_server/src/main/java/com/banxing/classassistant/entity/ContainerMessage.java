package com.banxing.classassistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 群组聊天消息，对应表 t_container_message。
 *
 * <p>消息直接挂在 {@code containerId} 上，没有"会话"这一层：
 * 一个群只有一个聊天，多拆一层只会多一次 join。
 *
 * <p>排序和轮询都用自增 {@code id}，不用 {@code createdAt} ——
 * 同一秒发的多条消息按时间排会乱序，按 id 排永远稳定。
 */
@Data
@TableName("t_container_message")
public class ContainerMessage {

    /** 文本消息 */
    public static final int TYPE_TEXT = 1;

    /** 图片 */
    public static final int TYPE_IMAGE = 2;

    /** 视频 */
    public static final int TYPE_VIDEO = 3;

    /** 文件（文档、压缩包等） */
    public static final int TYPE_FILE = 4;

    /** 接龙卡片，refId 指向 t_relay.id */
    public static final int TYPE_RELAY = 5;

    /** 抽签卡片，refId 指向 t_lottery.id */
    public static final int TYPE_LOTTERY = 6;

    /** 正常 */
    public static final int STATUS_NORMAL = 1;

    /** 已撤回 */
    public static final int STATUS_REVOKED = 0;

    /** 老师发的：senderId 是 t_user.id */
    public static final int FROM_TEACHER = 1;

    /** 学生发的：senderId 是 t_student.id */
    public static final int FROM_STUDENT = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long containerId;

    private Long senderId;

    /**
     * 发送者类型：1=老师 2=学生。
     *
     * <p><b>这一列不是"锦上添花"，是防串号的必需品。</b> t_user 和 t_student 是两套自增 id，
     * 两边都有 121 号。只看 sender_id 的话，"学生 121"和"老师 121"在历史消息里会变成
     * 同一个人 —— 而且不报错、不越权，只是名字悄悄换成了另一个人的。
     *
     * <p>列名是 sender_role，字段名故意叫 senderType：MessageVO 里已经有一个 senderRole
     * 表示"群主/班委/成员"，两个 role 混在一起，读代码的人一定会看错。
     */
    @TableField("sender_role")
    private Integer senderType;

    /** 发送者姓名快照。学生消息必填；老师消息为空，走 t_user 反查 */
    private String senderName;

    /** 文本正文；媒体消息为空串，内容看 mediaUrl */
    private String content;

    /** 媒体文件的相对路径，如 /files/container/1/202610/xxx.jpg */
    private String mediaUrl;

    /** 原始文件名，文件/视频气泡上展示 */
    private String fileName;

    /** 字节数 */
    private Long fileSize;

    /** 视频时长（秒）；图片和文件为 null */
    private Integer duration;

    /**
     * 卡片消息指向的业务 id：type=5 是 t_relay.id，type=6 是 t_lottery.id。
     *
     * <p>不塞进 content 的 JSON 里，是为了让"老版本客户端"和"新版本解析"
     * 不互相打架：老版本只读 content 显示成「[接龙] 春游报名」，
     * 新版本读 refId 去拉卡片状态。
     */
    private Long refId;

    /** 1=文本 2=图片 3=视频 4=文件 5=接龙卡片 6=抽签卡片 */
    private Integer type;

    /** 1=正常 0=已撤回 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
