-- 14_student_in_container.sql
-- 学生进入老师建的群：一个群，两端共用一份消息
--
-- 背景：v0.39 给学生端单独建了 t_class_message，结果老师端看不到学生在群里说什么，
-- 老师端的群邀请码对学生也无效 —— 因为那根本是两份数据。
-- 现在统一到 t_subject_container（老师建的"学科/班级群"），学生只是多了一种发送者。
--
-- 三件事：
--   1) t_container_message 加 sender_role / sender_name
--      —— 学生不是 t_user，sender_id 存 studentId，必须靠 role 区分，否则
--         t_user.id=121 和 t_student.id=121 会变成同一个人（静默串号）
--   2) 新建 t_student_container：学生"用邀请码加入非本班群"的记录
--      —— 本班的群不需要记录：花名册里有你，你就在群里
--   3) 把 t_class_message 里已有的消息搬进对应班级的容器，然后删表
--
-- 注意：ALTER 不可重复执行（MySQL 8 不支持 ADD COLUMN IF NOT EXISTS）。

USE `class_assistant`;

ALTER TABLE t_container_message
  ADD COLUMN sender_role TINYINT NOT NULL DEFAULT 1
    COMMENT '发送者类型：1=老师(t_user) 2=学生(t_student)' AFTER sender_id,
  ADD COLUMN sender_name VARCHAR(32) NULL
    COMMENT '发送者姓名快照，学生消息必填；老师消息为空走 t_user 反查' AFTER sender_role;

CREATE TABLE IF NOT EXISTS t_student_container (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  student_id   BIGINT UNSIGNED NOT NULL COMMENT 't_student.id',
  container_id BIGINT UNSIGNED NOT NULL COMMENT 't_subject_container.id',
  class_id     BIGINT UNSIGNED NULL COMMENT '加入时所在班，仅作展示',
  created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_student_container (student_id, container_id),
  KEY idx_container (container_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生用邀请码加入非本班群的记录';

-- 迁移上一版班级群里的消息（按 class_id 找该班的容器）
INSERT INTO t_container_message
  (container_id, sender_id, sender_role, sender_name, content, type, status, created_at, updated_at)
SELECT c.id, m.sender_id, m.sender_role, m.sender_name, m.content, 1, 1, m.created_at, m.created_at
FROM t_class_message m
JOIN t_subject_container c ON c.class_id = m.class_id AND c.status = 1
WHERE NOT EXISTS (
  SELECT 1 FROM t_container_message x
  WHERE x.container_id = c.id
    AND x.sender_role = 2
    AND x.sender_name = m.sender_name
    AND x.content = m.content
    AND x.created_at = m.created_at
);

-- 迁移完就删：留着这张表，下一个人会以为"班级群"还是它
DROP TABLE t_class_message;
