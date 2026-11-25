-- =============================================================
-- 班级助手 · 增量脚本 06：群聊消息支持图片 / 视频 / 文件
--
-- 执行时机：已跑过 05_container_message.sql 的库；只执行一次
-- 重复执行会报 ERROR 1060 (42S21): Duplicate column name 'media_url'，属正常
--
-- 设计前提：
--   1. 只加列，不改既有列的类型和约束（content 仍是 NOT NULL）
--   2. 媒体消息的 content 存空串 ''：这样列表渲染逻辑不用为 NULL 单独分支，
--      数据库也不用放宽 NOT NULL —— 文本消息照旧写正文，媒体消息正文为空
--   3. 文件本身不存库，只存相对路径。库里塞 BLOB 会让备份、主从、查询全部变慢，
--      而且 MySQL 的 max_allowed_packet 很容易先撞墙
-- =============================================================

USE `class_assistant`;

ALTER TABLE `t_container_message`
  ADD COLUMN `media_url` VARCHAR(500) DEFAULT NULL
      COMMENT '媒体文件的相对访问路径，如 /files/container/1/202610/xxx.jpg' AFTER `content`,
  ADD COLUMN `file_name` VARCHAR(128) DEFAULT NULL
      COMMENT '原始文件名（文件/视频展示用）' AFTER `media_url`,
  ADD COLUMN `file_size` BIGINT DEFAULT NULL
      COMMENT '文件字节数，前端格式化成 KB/MB' AFTER `file_name`,
  ADD COLUMN `duration`  INT DEFAULT NULL
      COMMENT '视频时长（秒）；图片和文件为 NULL' AFTER `file_size`;

-- type 的含义扩了，注释同步一下（类型和默认值都不动，只是让 DESC 看得懂）
ALTER TABLE `t_container_message`
  MODIFY COLUMN `type` TINYINT NOT NULL DEFAULT 1
      COMMENT '1=文本 2=图片 3=视频 4=文件',
  MODIFY COLUMN `content` VARCHAR(500) NOT NULL
      COMMENT '文本正文；图片/视频/文件消息为空串，内容看 media_url';

-- 核对：应该看到 4 行新列 + type 的新注释
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 't_container_message'
ORDER BY ORDINAL_POSITION;
