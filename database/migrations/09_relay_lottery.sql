-- =============================================================
-- 班级助手 · 增量脚本 09：群互动（接龙 / 抽签）
--
-- 执行时机：已跑过 08_user_unit.sql 的库；只执行一次
-- 重复执行会报 Duplicate column / Table already exists，属正常
--
-- 设计前提：
--   1. 接龙和抽签都挂在群（t_subject_container）上，不挂在班级上 ——
--      同一个班可能既有班级群又有课程群，接龙应该属于"某个群"，不是"某个班"。
--      但 class_id 要冗余一份：代填要拉学生名单，抽签要抽学生名单，
--      每次都从 container 反查 class_id 只是多一次 join。
--   2. 接龙条目同时支持两种人：user_id（自助接龙）和 student_id（老师代填）。
--      两列都可空，靠两个唯一索引分别防重 —— MySQL 的唯一索引允许多个 NULL，
--      所以"代填行 user_id 全是 NULL"不会互相冲突。
--   3. 抽签结果存 student_name 快照：学生转班、退学、改名之后，
--      历史记录里的名字不该跟着变。
--   4. 消息表的 ref_id 指向 t_relay.id 或 t_lottery.id，由 type 决定指向哪张表。
--      不加外键：消息可以先于内容存在（撤回），外键会让删除变复杂。
-- =============================================================

-- -------------------------------------------------------------
-- 1) 消息表：加 ref_id，扩 type 含义
-- -------------------------------------------------------------
USE `class_assistant`;

ALTER TABLE `t_container_message`
  ADD COLUMN `ref_id` BIGINT DEFAULT NULL
      COMMENT '卡片消息指向的业务 id：type=5 是 t_relay.id，type=6 是 t_lottery.id' AFTER `duration`;

ALTER TABLE `t_container_message`
  MODIFY COLUMN `type` TINYINT NOT NULL DEFAULT 1
      COMMENT '1=文本 2=图片 3=视频 4=文件 5=接龙卡片 6=抽签卡片';

-- -------------------------------------------------------------
-- 2) 接龙
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_relay` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT,
  `container_id`    BIGINT       NOT NULL COMMENT '所属群',
  `class_id`        BIGINT       DEFAULT NULL COMMENT '冗余的班级 id，用于拉学生名单；课程群可能为空',
  `creator_id`      BIGINT       NOT NULL COMMENT '发起人',
  `title`           VARCHAR(30)  NOT NULL COMMENT '接龙主题',
  `description`     VARCHAR(500) NOT NULL DEFAULT '' COMMENT '说明，纯文本',
  `entry_format`    TINYINT      NOT NULL DEFAULT 1 COMMENT '1序号 2数字 3手机号 4短文本 5单选',
  `format_min`      INT          DEFAULT NULL COMMENT '格式=2 时的最小值',
  `format_max`      INT          DEFAULT NULL COMMENT '格式=2 时的最大值',
  `options`         VARCHAR(500) DEFAULT NULL COMMENT '格式=5 时的选项，JSON 数组字符串',
  `max_count`       INT          DEFAULT NULL COMMENT '人数上限，NULL=不限',
  `deadline`        DATETIME     DEFAULT NULL COMMENT '截止时间，NULL=不限',
  `allow_self_edit` TINYINT      NOT NULL DEFAULT 1 COMMENT '参与者截止前能否改自己的条目',
  `status`          TINYINT      NOT NULL DEFAULT 1 COMMENT '1进行中 4已结束 5已取消；已满/已截止是算出来的，不落库',
  `created_at`      DATETIME     NOT NULL,
  `updated_at`      DATETIME     DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_container` (`container_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='接龙';

CREATE TABLE IF NOT EXISTS `t_relay_entry` (
  `id`            BIGINT      NOT NULL AUTO_INCREMENT,
  `relay_id`      BIGINT      NOT NULL,
  `user_id`       BIGINT      DEFAULT NULL COMMENT '自助接龙的人；代填时为 NULL',
  `student_id`    BIGINT      DEFAULT NULL COMMENT '代填的学生；自助时为 NULL',
  `seq`           INT         NOT NULL COMMENT '序号，从 1 开始，按提交顺序',
  `value`         VARCHAR(64) NOT NULL DEFAULT '' COMMENT '填写内容；格式=1 时为空串',
  `name_snapshot` VARCHAR(32) NOT NULL COMMENT '提交时的姓名快照，改名不影响历史',
  `status`        TINYINT     NOT NULL DEFAULT 1 COMMENT '1正常 0已撤回',
  `created_at`    DATETIME    NOT NULL,
  `updated_at`    DATETIME    DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_relay_user` (`relay_id`, `user_id`),
  UNIQUE KEY `uk_relay_student` (`relay_id`, `student_id`),
  KEY `idx_relay` (`relay_id`, `status`, `seq`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='接龙条目';

-- -------------------------------------------------------------
-- 3) 抽签
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_lottery` (
  `id`             BIGINT      NOT NULL AUTO_INCREMENT,
  `container_id`   BIGINT      NOT NULL,
  `class_id`       BIGINT      DEFAULT NULL,
  `creator_id`     BIGINT      NOT NULL,
  `kind`           TINYINT     NOT NULL DEFAULT 1 COMMENT '1随机点名 2随机分组',
  `source`         TINYINT     NOT NULL DEFAULT 1 COMMENT '1当前班学生 2手动勾选',
  `pool_snapshot`  TEXT        NOT NULL COMMENT '发布时的候选名单快照，JSON: [{id,name}]',
  `draw_count`     INT         NOT NULL DEFAULT 1 COMMENT '点名模式每次抽几人',
  `group_count`    INT         NOT NULL DEFAULT 2 COMMENT '分组模式分几组',
  `dedupe`         TINYINT     NOT NULL DEFAULT 1 COMMENT '1=已抽中的人不再进入下一轮',
  `public_result`  TINYINT     NOT NULL DEFAULT 1 COMMENT '1=群内公开 0=仅发起人可见',
  `status`         TINYINT     NOT NULL DEFAULT 1 COMMENT '1未开始 2已开奖 3已取消',
  `round`          INT         NOT NULL DEFAULT 0 COMMENT '已抽轮次，点名模式每抽一次 +1',
  `seed`           BIGINT      DEFAULT NULL COMMENT '开奖用的随机种子，存下来可复现',
  `version`        INT         NOT NULL DEFAULT 0 COMMENT '乐观锁，防两人同时点开始抽',
  `drawn_at`       DATETIME    DEFAULT NULL,
  `created_at`     DATETIME    NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_container` (`container_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='抽签';

CREATE TABLE IF NOT EXISTS `t_lottery_result` (
  `id`           BIGINT      NOT NULL AUTO_INCREMENT,
  `lottery_id`   BIGINT      NOT NULL,
  `round`        INT         NOT NULL DEFAULT 1 COMMENT '第几轮，点名模式每次抽签一轮',
  `student_id`   BIGINT      DEFAULT NULL COMMENT '手动名单里的人可能不是本班学生，允许为空',
  `student_name` VARCHAR(32) NOT NULL COMMENT '姓名快照',
  `group_no`     INT         DEFAULT NULL COMMENT '分组模式第几组；点名模式为 NULL',
  -- 不能叫 rank：MySQL 8 把 RANK 当窗口函数保留字，
  -- 不写反引号时 SELECT 列清单会在 rank 处直接语法报错
  `sort_no`      INT         NOT NULL DEFAULT 1 COMMENT '同一轮内的顺序',
  PRIMARY KEY (`id`),
  KEY `idx_lottery` (`lottery_id`, `round`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='抽签结果';

-- -------------------------------------------------------------
-- 4) 核对
-- -------------------------------------------------------------
SELECT TABLE_NAME, TABLE_COMMENT
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('t_relay', 't_relay_entry', 't_lottery', 't_lottery_result')
ORDER BY TABLE_NAME;

SELECT COLUMN_NAME, COLUMN_TYPE, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 't_container_message'
  AND COLUMN_NAME IN ('type', 'ref_id')
ORDER BY ORDINAL_POSITION;
