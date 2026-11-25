-- =============================================================
-- 08 给用户加「单位」字段
-- -------------------------------------------------------------
-- 背景：个人中心里那一行原来是「班级」，但这是**教师端** ——
--       老师挂在班级上说不通，他挂的是学校 / 单位。
--       班级本来就有自己的入口（通讯录 → ⋯ → 班级信息），
--       不需要在个人信息里再放一个。
--
-- 为什么不复用 t_class.name：一个老师可以带好几个班（多班级改造之后
--       更是常态），而「单位」只有一个，两者不是一回事。
--       而且学生也会出现在 t_user 里，学生没有班级可填。
--
-- 为什么可空：这是选填信息，不填也能正常用。
--       用 DEFAULT '' 会让「没填」和「填了空」变成一个意思，
--       以后想做「完善资料」提示就没法区分了。
--
-- 幂等：**只执行一次**。再跑会报
--       ERROR 1060 (42S21): Duplicate column name 'unit'
--       这是正常的，说明已经加过了。
--
-- 用法（库名写在命令行，脚本里不写 USE）：
--   mysql -uroot -p --default-character-set=utf8mb4 class_assistant < 08_user_unit.sql
-- =============================================================

USE `class_assistant`;

ALTER TABLE `t_user`
  ADD COLUMN `unit` VARCHAR(64) DEFAULT NULL COMMENT '单位 / 学校，老师填写' AFTER `phone`;

-- 核对：应该看到 1 行
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 't_user'
  AND COLUMN_NAME = 'unit';
