-- =============================================================
-- 12_direct_message.sql
-- 老师 ↔ 学生的一对一私信
-- =============================================================
--
-- 和群聊（t_container_message）刻意分开，不是偷懒：
--   1) 群聊的成员是 t_user（有账号的人），学生没有账号，塞不进 t_container_member
--   2) 群聊要处理禁言、拉黑、精华、接龙卡片，私信用不上，混在一张表里全是噪声
--   3) 未读数的算法完全不同：群聊是"每个成员各自读到哪"，私信是"这条消息对方读没读"
--
-- 线程键是 (class_id, student_id)，不是 (teacher_id, student_id)：
-- 一个班只有一个班主任，用 class_id 顺带就把越权检查变成了一句 where。

USE `class_assistant`;

CREATE TABLE IF NOT EXISTS t_direct_message (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键，同时当轮询游标',
  class_id    BIGINT UNSIGNED NOT NULL                COMMENT '所属班级',
  student_id  BIGINT UNSIGNED NOT NULL                COMMENT '对话的学生',
  teacher_id  BIGINT UNSIGNED NOT NULL                COMMENT '对话的老师（班主任）',
  sender_role TINYINT         NOT NULL                COMMENT '1=老师发的 2=学生发的',
  content     VARCHAR(1000)   NOT NULL                COMMENT '纯文本，最多 500 字（业务层卡）',
  read_at     DATETIME        DEFAULT NULL            COMMENT '对方读的时间；NULL=未读',
  created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  -- 拉一整个线程：where class_id=? and student_id=? order by id
  KEY idx_thread (class_id, student_id, id),
  -- 未读数：学生侧数"老师发的且没读"，老师侧数"学生发的且没读"
  KEY idx_unread (student_id, sender_role, read_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='师生私信';
