-- ============================================================
-- 13 班级群聊消息（学生端）
--
-- 为什么不是复用 t_container_message：
--   那张表的成员是 t_container_member.user_id → t_user。学生不是 t_user 行
--   （见 api-contract §9.9.1），进不了成员表，也发不了言。
--   硬把 studentId 塞进 sender_id 更糟：t_user.id 和 t_student.id 是两套自增，
--   121 号学生和 121 号老师会撞成同一个人，历史消息会显示错误的名字。
--
-- 为什么没有会话表 / 成员表：
--   一个班就是一个群。群名 = 班级名，成员 = 这个班的花名册。
--   再建 t_class_group / t_class_group_member 只是把 t_class 和 t_student
--   复制一遍，还得处理"转班了群成员怎么变"。
--
-- 为什么存 sender_name 快照：
--   名字要按角色去两张表查（老师查 t_user、学生查 t_student），
--   一条消息两次 join 不划算；而且学生退班/改名之后，历史消息不该跟着变空。
--   聊天记录里的"谁说的"是当时的事实，不是现在的状态。
-- ============================================================

USE `class_assistant`;

CREATE TABLE IF NOT EXISTS `t_class_message` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `class_id`    BIGINT UNSIGNED NOT NULL                COMMENT '所属班级',
  `sender_role` TINYINT         NOT NULL                COMMENT '1=老师 2=学生',
  `sender_id`   BIGINT UNSIGNED NOT NULL                COMMENT 'role=1 → t_user.id；role=2 → t_student.id',
  `sender_name` VARCHAR(32)     NOT NULL                COMMENT '发送时的名字快照',
  `content`     VARCHAR(1000)   NOT NULL                COMMENT '文本内容',
  `created_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_class_id` (`class_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='班级群聊消息（学生端）';
