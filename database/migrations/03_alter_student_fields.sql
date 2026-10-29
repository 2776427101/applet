-- =============================================================
-- 班级助手 · 增量脚本 03：学生表扩展 9 个可空字段
--
-- 执行时机：已经跑过 01_schema.sql 的库（有数据也不要紧，ADD COLUMN 不动老数据）
-- 执行次数：**只执行一次**。再跑一次会报
--           ERROR 1060 (42S21): Duplicate column name 'dormitory'
--           这是正常的，说明已经加过了，不用管。
--
-- 为什么加这 9 个字段：见 docs/step8-student-import.md
-- 全部 DEFAULT NULL —— 老数据没有这些信息，「没填」和「填了空」必须能区分，
-- 用 NOT NULL DEFAULT '' 会让 47 个老学生全变成「手机号是空字符串」。
-- =============================================================

USE `class_assistant`;

ALTER TABLE `t_student`
  -- 住宿
  ADD COLUMN `dormitory`          VARCHAR(32)  DEFAULT NULL COMMENT '宿舍，如 3栋502'      AFTER `student_no`,
  -- 联系方式
  ADD COLUMN `phone`              VARCHAR(20)  DEFAULT NULL COMMENT '学生本人手机号'        AFTER `dormitory`,
  ADD COLUMN `wechat`             VARCHAR(64)  DEFAULT NULL COMMENT '微信号'                AFTER `phone`,
  ADD COLUMN `family_phone1`      VARCHAR(20)  DEFAULT NULL COMMENT '家人电话 1'            AFTER `wechat`,
  ADD COLUMN `family_phone2`      VARCHAR(20)  DEFAULT NULL COMMENT '家人电话 2'            AFTER `family_phone1`,
  -- 学籍与其他
  ADD COLUMN `training_direction` VARCHAR(64)  DEFAULT NULL COMMENT '培训方向，如 软件开发' AFTER `family_phone2`,
  ADD COLUMN `nation`             VARCHAR(32)  DEFAULT NULL COMMENT '民族，如 汉族'          AFTER `training_direction`,
  ADD COLUMN `political_status`   VARCHAR(32)  DEFAULT NULL COMMENT '政治面貌，如 共青团员'  AFTER `nation`,
  -- 存原文而不是 0/1：名单里可能写「是」「否」「已递交申请」，统一成布尔会丢信息
  ADD COLUMN `party_activist`     VARCHAR(32)  DEFAULT NULL COMMENT '入党积极分子，存原文'  AFTER `political_status`;

-- 核对：应该看到 9 行新字段
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'class_assistant'
  AND TABLE_NAME = 't_student'
  AND COLUMN_NAME IN ('dormitory', 'phone', 'wechat', 'family_phone1', 'family_phone2',
                      'training_direction', 'nation', 'political_status', 'party_activist')
ORDER BY ORDINAL_POSITION;
