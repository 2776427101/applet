-- =============================================================
-- 班级助手 · 增量脚本 05：群组聊天消息
--
-- 执行时机：已跑过 01_schema.sql 与 04_subject_container.sql 的库；只执行一次
-- 重复执行会报 ERROR 1050 (42S01): Table 't_container_message' already exists，属正常
--
-- 设计前提：
--   1. 只 CREATE，不改任何既有表
--   2. 不做会话表：消息直接挂在 container_id 上。
--      t_subject_container.session_id 那个预留字段就让它空着 —— 一个群只有一个聊天，
--      再拆一层「会话」除了多一次 join 没有任何收益
--   3. 不建外键：与 01/04 一致，完整性由 Service 层保证
-- =============================================================

-- -------------------------------------------------------------
-- 群组聊天消息
--
-- 为什么用自增 id 当排序游标，而不是 created_at：
--   同一秒内的多条消息用时间排序会出现「顺序随机」，而 id 天然单调。
--   前端轮询用 afterId=最后一条的 id，增量拉取，不用重复传整页。
--
-- idx_container_id (container_id, id) 一个索引覆盖三种查询：
--   最新 N 条（ORDER BY id DESC）、增量（id > ?）、翻历史（id < ?）
-- -------------------------------------------------------------
USE `class_assistant`;

CREATE TABLE `t_container_message` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '消息 id，同时用作排序/轮询游标',
  `container_id` BIGINT UNSIGNED NOT NULL COMMENT '所属群组',
  `sender_id`    BIGINT UNSIGNED NOT NULL COMMENT '发送者 user_id',
  `content`      VARCHAR(500)    NOT NULL COMMENT '文本内容（本期只有文本，无表情包/图片）',
  `type`         TINYINT         NOT NULL DEFAULT 1 COMMENT '1=文本；预留给将来的图片/文件',
  `status`       TINYINT         NOT NULL DEFAULT 1 COMMENT '1=正常 0=已撤回',
  `created_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_container_id` (`container_id`, `id`),
  KEY `idx_sender` (`sender_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '群组聊天消息';

-- 核对：应该看到 1 行
SELECT TABLE_NAME, TABLE_COMMENT
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 't_container_message';
