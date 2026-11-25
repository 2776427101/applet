-- =============================================================
-- 班级助手 · 数据库结构 v0.1
-- 数据库：MySQL 8.0+（utf8mb4）
-- 执行顺序：先 01_schema.sql，再 02_seed.sql（可选，仅本地测试）
-- 说明：本期只建首页闭环需要的 5 张表，其余模块的表做到对应模块时再加
-- =============================================================

CREATE DATABASE IF NOT EXISTS `class_assistant`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

USE `class_assistant`;

-- 首次初始化才执行下面 5 行；已经有数据时千万别跑，会清空
DROP TABLE IF EXISTS `t_todo`;
DROP TABLE IF EXISTS `t_leave_request`;
DROP TABLE IF EXISTS `t_student`;
DROP TABLE IF EXISTS `t_class`;
DROP TABLE IF EXISTS `t_user`;

-- -------------------------------------------------------------
-- 1. 用户表：老师 / 学生共用一张表，靠 role 区分
-- -------------------------------------------------------------
CREATE TABLE `t_user` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `openid`        VARCHAR(64)     NOT NULL                COMMENT '微信 openid，登录唯一标识',
  `unionid`       VARCHAR(64)     DEFAULT NULL            COMMENT '微信 unionid，同主体多应用时用',
  `role`          TINYINT         NOT NULL DEFAULT 1      COMMENT '角色 1=老师 2=学生',
  `name`          VARCHAR(32)     NOT NULL DEFAULT ''     COMMENT '姓名',
  `phone`         VARCHAR(20)     DEFAULT NULL            COMMENT '手机号',
  `avatar`        VARCHAR(255)    DEFAULT NULL            COMMENT '头像 URL',
  `status`        TINYINT         NOT NULL DEFAULT 1      COMMENT '1=正常 0=禁用',
  `last_login_at` DATETIME        DEFAULT NULL            COMMENT '最后登录时间',
  `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_openid` (`openid`),
  KEY `idx_role_status` (`role`, `status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户（老师/学生）';

-- -------------------------------------------------------------
-- 2. 班级表
-- -------------------------------------------------------------
CREATE TABLE `t_class` (
  `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name`             VARCHAR(32)     NOT NULL                COMMENT '班级名，如 628班',
  `grade`            VARCHAR(16)     NOT NULL DEFAULT ''     COMMENT '年级，如 高一',
  `head_teacher_id`  BIGINT UNSIGNED NOT NULL                COMMENT '班主任 t_user.id',
  `term_start_date`  DATE            NOT NULL                COMMENT '本学期第一周的周一，用来算第几周',
  `invite_code`      VARCHAR(16)     DEFAULT NULL            COMMENT '学生加入班级的邀请码',
  `status`           TINYINT         NOT NULL DEFAULT 1      COMMENT '1=正常 0=已归档',
  `created_at`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_invite_code` (`invite_code`),
  KEY `idx_head_teacher` (`head_teacher_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '班级';

-- -------------------------------------------------------------
-- 3. 学生表
-- -------------------------------------------------------------
CREATE TABLE `t_student` (
  `id`                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `class_id`           BIGINT UNSIGNED NOT NULL                COMMENT '所属班级',
  `name`               VARCHAR(32)     NOT NULL                COMMENT '姓名',
  `gender`             TINYINT         NOT NULL DEFAULT 0      COMMENT '0=未知 1=男 2=女',
  `birthday`           DATE            DEFAULT NULL            COMMENT '生日（年份可只作参考）',
  `student_no`         VARCHAR(32)     DEFAULT NULL            COMMENT '学号（一律字符串，保留前导零）',
  -- 下面 9 个字段由 03_alter_student_fields.sql 增加，支持名单导入
  `dormitory`          VARCHAR(32)     DEFAULT NULL            COMMENT '宿舍，如 3栋502',
  `phone`              VARCHAR(20)     DEFAULT NULL            COMMENT '学生本人手机号',
  `wechat`             VARCHAR(64)     DEFAULT NULL            COMMENT '微信号',
  `family_phone1`      VARCHAR(20)     DEFAULT NULL            COMMENT '家人电话 1',
  `family_phone2`      VARCHAR(20)     DEFAULT NULL            COMMENT '家人电话 2',
  `training_direction` VARCHAR(64)     DEFAULT NULL            COMMENT '培训方向，如 软件开发',
  `nation`             VARCHAR(32)     DEFAULT NULL            COMMENT '民族，如 汉族',
  `political_status`   VARCHAR(32)     DEFAULT NULL            COMMENT '政治面貌，如 共青团员',
  `party_activist`     VARCHAR(32)     DEFAULT NULL            COMMENT '入党积极分子，存原文',
  `status`             TINYINT         NOT NULL DEFAULT 1      COMMENT '1=在读 0=已离班',
  `created_at`         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_class_status` (`class_id`, `status`),
  KEY `idx_birthday` (`birthday`),
  UNIQUE KEY `uk_class_student_no` (`class_id`, `student_no`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '学生';

-- -------------------------------------------------------------
-- 4. 请假表
-- -------------------------------------------------------------
CREATE TABLE `t_leave_request` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `class_id`   BIGINT UNSIGNED NOT NULL                COMMENT '班级（冗余，避免每次都 join 学生表）',
  `student_id` BIGINT UNSIGNED NOT NULL                COMMENT '学生',
  `type`       TINYINT         NOT NULL DEFAULT 1      COMMENT '1=病假 2=事假 3=其他',
  `start_at`   DATETIME        NOT NULL                COMMENT '请假开始时间',
  `end_at`     DATETIME        NOT NULL                COMMENT '请假结束时间',
  `reason`     VARCHAR(255)    DEFAULT NULL            COMMENT '请假原因',
  `status`     TINYINT         NOT NULL DEFAULT 0      COMMENT '0=待审批 1=已通过 2=已驳回 3=已销假',
  `audit_remark` VARCHAR(255)  DEFAULT NULL            COMMENT '审批备注',
  `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_class_start` (`class_id`, `start_at`),
  KEY `idx_student` (`student_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '请假记录';

-- -------------------------------------------------------------
-- 5. 待办表
-- -------------------------------------------------------------
CREATE TABLE `t_todo` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `teacher_id` BIGINT UNSIGNED NOT NULL                COMMENT '所属老师 t_user.id',
  `class_id`   BIGINT UNSIGNED NOT NULL                COMMENT '所属班级',
  `title`      VARCHAR(128)    NOT NULL                COMMENT '待办内容',
  `done`       TINYINT         NOT NULL DEFAULT 0      COMMENT '0=未完成 1=已完成',
  `due_at`     DATETIME        DEFAULT NULL            COMMENT '截止时间',
  `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_teacher_done` (`teacher_id`, `done`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '教师待办';

-- -------------------------------------------------------------
-- 6. 考试表：一次考试一条
-- -------------------------------------------------------------
CREATE TABLE `t_exam` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `class_id`   BIGINT UNSIGNED NOT NULL                COMMENT '所属班级',
  `name`       VARCHAR(64)     NOT NULL                COMMENT '考试名称，如 第一次月考',
  `type`       TINYINT         NOT NULL DEFAULT 2      COMMENT '1=周测 2=月考 3=期中 4=期末',
  `exam_date`  DATE            NOT NULL                COMMENT '考试日期',
  `subjects`   VARCHAR(255)    NOT NULL                COMMENT '科目，逗号分隔，如 语文,数学,英语',
  `full_score` VARCHAR(255)    DEFAULT NULL            COMMENT '各科满分，JSON，如 {"语文":150}；NULL 表示全部 100',
  `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_class_name` (`class_id`, `name`),
  KEY `idx_class_date` (`class_id`, `exam_date`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '考试';

-- -------------------------------------------------------------
-- 7. 成绩表：一行 = 某学生某科的成绩（窄表，加科目不用改结构）
-- -------------------------------------------------------------
CREATE TABLE `t_score` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `class_id`   BIGINT UNSIGNED NOT NULL                COMMENT '所属班级（冗余，便于按班统计）',
  `exam_id`    BIGINT UNSIGNED NOT NULL                COMMENT '考试',
  `student_id` BIGINT UNSIGNED NOT NULL                COMMENT '学生',
  `subject`    VARCHAR(16)     NOT NULL                COMMENT '科目名',
  `score`      DECIMAL(6,1)    DEFAULT NULL            COMMENT '分数，NULL=缺考',
  `full_score` DECIMAL(6,1)    NOT NULL DEFAULT 100    COMMENT '满分',
  `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_exam_student_subject` (`exam_id`, `student_id`, `subject`),
  KEY `idx_class_exam` (`class_id`, `exam_id`),
  KEY `idx_student` (`student_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '成绩';

-- -------------------------------------------------------------
-- 8. 座位表：一行 = 一个格子（坐没坐人都存，空座位 student_id 为 NULL）
--
-- 为什么空座位也要存一行：
--   如果只存有人的座位，rows/cols 就只能靠 max(row_no)/max(col_no) 反推。
--   最后一排整排没人时，排数会凭空少一排 —— 老师明明设了 6 排，
--   保存完再打开变成 5 排。存满格子就没这个问题：有几个格子就是几行几列。
--   代价是 6×8 的班多存十几行，一年下来也就几百行，可以忽略。
-- -------------------------------------------------------------
CREATE TABLE `t_seat` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `class_id`   BIGINT UNSIGNED NOT NULL                COMMENT '所属班级',
  `row_no`     TINYINT         NOT NULL                COMMENT '第几排，从 1 开始，1 排在最靠近讲台',
  `col_no`     TINYINT         NOT NULL                COMMENT '第几列，从 1 开始，面对讲台从左往右',
  `student_id` BIGINT UNSIGNED DEFAULT NULL            COMMENT '学生，NULL=空座位',
  `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_class_row_col` (`class_id`, `row_no`, `col_no`),
  KEY `idx_class_student` (`class_id`, `student_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '座位';

-- =============================================================
-- 说明
-- 1. 不建外键约束：国内业务项目常见做法，避免删改数据时的级联麻烦，
--    完整性靠 Service 层保证（class_id 一定要从 token 反查，不能信前端）。
-- 2. 所有业务表都带 class_id，查询必须带上它，否则会越权看到别的班。
-- 3. "今日生日" 用 MONTH(birthday)/DAY(birthday) 查，索引帮不上忙；
--    学生量级小（几十到几百）无所谓，上万了再加 birthday_md 冗余字段。
-- 4. 时间统一存服务器本地时间（Asia/Shanghai），不要存 UTC。
-- 5. t_score.score 用 DECIMAL(6,1) 而不是 INT：有些科目满分 150，
--    会出现 128.5 这种半分。用 FLOAT 会有 0.1+0.2=0.30000000000000004 的精度问题。
-- 6. 成绩存成"窄表"（一行 = 一个学生的一科）而不是宽表（一行一个学生、列是科目）：
--    宽表加科目要改表结构，而且不同考试科目还不一样。窄表加科目只是多几行数据。
-- 7. t_seat 没给 student_id 建唯一键，而是建普通索引：MySQL 的唯一键允许多个 NULL，
--    所以建唯一键也能跑，但"一个学生坐两个位置"这种错在 Service 层校验更好 ——
--    唯一键只会甩一句 Duplicate entry，Service 能说出"王小明被排到了两个座位"。
-- =============================================================
