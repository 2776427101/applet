-- =============================================================
-- 班级助手 · 增量脚本 04：学科班级容器
--
-- 执行时机：已跑过 01_schema.sql 的库；**只执行一次**
-- 重复执行会报 ERROR 1050 (42S01): Table 'xxx' already exists，属正常
--
-- 设计前提（见 docs/subject-container-design.md）：
--   1. 只 CREATE，不 ALTER / 不 DROP 任何既有表 —— 现有 8 张表零影响
--   2. 容器是「学科 × 班级/课程」的成员容器，与行政班 t_class 是两个维度，
--      用 class_id 弱关联（可空，支持跨班选修）
--   3. 不建外键：与 01_schema.sql 一致，完整性由 Service 层保证
-- =============================================================

-- 不写 USE `class_assistant`：本项目的回归测试要跑在副本库上
-- （见 engineering-notes「用副本库测，别拿真库试」），
-- 脚本里一旦写死库名，`mysql 副本库 < 04_xxx.sql` 会静默把表建到真库里。
-- 正确用法是把库名写在命令行：
--   mysql -uroot -p --default-character-set=utf8mb4 class_assistant < 04_subject_container.sql

-- -------------------------------------------------------------
-- 1. 学科容器主体
-- -------------------------------------------------------------
USE `class_assistant`;

CREATE TABLE `t_subject_container` (
  `id`                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `subject_name`         VARCHAR(32)     NOT NULL                COMMENT '学科名称，必填，如 数学',
  `name`                 VARCHAR(64)     NOT NULL                COMMENT '容器显示名，默认「学科名 · 班级名」，可改',
  `class_id`             BIGINT UNSIGNED DEFAULT NULL            COMMENT '关联行政班 t_class.id，NULL=不关联',
  `owner_id`             BIGINT UNSIGNED NOT NULL                COMMENT '群主（负责老师）t_user.id',
  `invite_code`          CHAR(10)        NOT NULL                COMMENT '10 位邀请码，全局唯一，大写且不含 0/O/1/I',
  `invite_expire_at`     DATETIME        DEFAULT NULL            COMMENT '邀请码截止时间，NULL=永久有效',
  `join_password_hash`   VARCHAR(255)    DEFAULT NULL            COMMENT '加入密码 PBKDF2 哈希，NULL=无密码',
  `join_password_set_at` DATETIME        DEFAULT NULL            COMMENT '密码最近设置时间（仅展示）',
  `session_id`           BIGINT UNSIGNED DEFAULT NULL            COMMENT '预留：聊天会话 ID，本期不做 IM',
  `status`               TINYINT         NOT NULL DEFAULT 1      COMMENT '1=正常 0=已解散',
  `dissolved_at`         DATETIME        DEFAULT NULL            COMMENT '解散时间',
  `dissolved_by`         BIGINT UNSIGNED DEFAULT NULL            COMMENT '解散操作人',
  `created_at`           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_invite_code` (`invite_code`),
  KEY `idx_owner_status` (`owner_id`, `status`),
  KEY `idx_class` (`class_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '学科班级容器';

-- -------------------------------------------------------------
-- 2. 容器成员：群主(1) / 班委即管理员(2) / 成员(3)
--
-- role 和 status 必须分开：
--   一个管理员被移除后，role 还是 2 但 status=2，语义是「曾经是管理员，现已不在群」。
--   合成一个字段就会出现「已移除的管理员」到底算不算管理员的歧义。
--
-- uk_container_user 不只是防重复：
--   两个请求同时点「加入」时，第二个会撞这个键，Service 捕获后返回「你已在该容器中」，
--   不会插出两行。这是并发保护，不是可选的索引。
-- -------------------------------------------------------------
CREATE TABLE `t_container_member` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `container_id` BIGINT UNSIGNED NOT NULL                COMMENT '容器',
  `user_id`      BIGINT UNSIGNED NOT NULL                COMMENT '成员 t_user.id',
  `role`         TINYINT         NOT NULL DEFAULT 3      COMMENT '1=群主 2=管理员(班委) 3=成员',
  `status`       TINYINT         NOT NULL DEFAULT 1      COMMENT '1=在群 2=被移除 3=已退出',
  `nickname`     VARCHAR(32)     DEFAULT NULL            COMMENT '群内昵称，空则用 t_user.name',
  `join_source`  TINYINT         NOT NULL DEFAULT 1      COMMENT '1=邀请码 2=创建者 3=管理员添加',
  `joined_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
  -- 禁言只用一个到期时间字段，NULL 表示没禁言。
  -- 不另加 is_permanent：两个字段会出现「永久禁言但又有到期时间」的矛盾状态。
  `mute_until`   DATETIME        DEFAULT NULL            COMMENT '禁言到期；NULL=未禁言；9999-12-31 23:59:59=永久',
  `mute_reason`  VARCHAR(100)    DEFAULT NULL            COMMENT '禁言原因',
  `mute_by`      BIGINT UNSIGNED DEFAULT NULL            COMMENT '禁言操作人',
  `mute_at`      DATETIME        DEFAULT NULL            COMMENT '禁言操作时间',
  `removed_by`   BIGINT UNSIGNED DEFAULT NULL            COMMENT '移除操作人',
  `removed_at`   DATETIME        DEFAULT NULL            COMMENT '移除时间',
  `created_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_container_user` (`container_id`, `user_id`),
  KEY `idx_user_status` (`user_id`, `status`),
  KEY `idx_container_role` (`container_id`, `role`, `status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '容器成员';

-- -------------------------------------------------------------
-- 3. 黑名单（只有群主可见、可操作）
--
-- 为什么单独一张表，而不是在成员表加 blacklisted 字段：
--   黑名单是「禁止加入」的独立状态，和「是不是成员」正交。
--   一个人可以被拉黑但从来没有成员行（例如被移除后成员行被清理），
--   也可以先被拉黑、后来被放出来，再凭邀请码加入。
--
-- 释放用 released_at 标记而不是删行：谁在什么时候把谁放出来，要留档。
-- 再次拉黑同一个人时 UPDATE 原行（released_at 置 NULL），避免撞 uk。
-- -------------------------------------------------------------
CREATE TABLE `t_container_blacklist` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `container_id` BIGINT UNSIGNED NOT NULL                COMMENT '容器',
  `user_id`      BIGINT UNSIGNED NOT NULL                COMMENT '被拉黑用户 t_user.id',
  `reason`       VARCHAR(100)    DEFAULT NULL            COMMENT '拉黑原因（移除时可填）',
  `operator_id`  BIGINT UNSIGNED NOT NULL                COMMENT '拉黑操作人',
  `created_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '拉黑时间',
  `released_by`  BIGINT UNSIGNED DEFAULT NULL            COMMENT '解除拉黑操作人',
  `released_at`  DATETIME        DEFAULT NULL            COMMENT '解除时间，NULL=仍在黑名单',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_container_user` (`container_id`, `user_id`),
  KEY `idx_container_released` (`container_id`, `released_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '容器黑名单';

-- -------------------------------------------------------------
-- 4. 精华消息
--
-- 本期没有聊天模块，所以「指定某条消息」暂时无消息可指：
--   message_id 预留给聊天上线后关联 t_chat_message，
--   现在由班委/群主把内容（笔记、要求、要点）直接写进 content 快照。
--   聊天上线后两者可以共存：有 message_id 的显示「查看原消息」，没有的当纯文本精华。
-- -------------------------------------------------------------
CREATE TABLE `t_container_highlight` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `container_id` BIGINT UNSIGNED NOT NULL                COMMENT '容器',
  `message_id`   BIGINT UNSIGNED DEFAULT NULL            COMMENT '预留：聊天消息 ID，本期为 NULL',
  `content`      VARCHAR(500)    NOT NULL                COMMENT '精华内容（消息快照或手写要点）',
  `creator_id`   BIGINT UNSIGNED NOT NULL                COMMENT '收录人',
  `status`       TINYINT         NOT NULL DEFAULT 1      COMMENT '1=有效 0=已取消',
  `created_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `canceled_by`  BIGINT UNSIGNED DEFAULT NULL            COMMENT '取消人',
  `canceled_at`  DATETIME        DEFAULT NULL            COMMENT '取消时间',
  PRIMARY KEY (`id`),
  KEY `idx_container_status_created` (`container_id`, `status`, `created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '容器精华消息';

-- -------------------------------------------------------------
-- 5. 加入密码错误计数
--
-- 不用内存计数：重启就清零、多实例各算各的，等于没有限制。
-- 按 (container_id, user_id) 计：换账号爆破仍可能，但成本已远高于收益，
-- 本期不做 IP 维度（小程序拿不到可信 IP）。
-- -------------------------------------------------------------
CREATE TABLE `t_container_join_attempt` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `container_id` BIGINT UNSIGNED NOT NULL,
  `user_id`      BIGINT UNSIGNED NOT NULL,
  `fail_count`   INT             NOT NULL DEFAULT 0  COMMENT '连续失败次数',
  `locked_until` DATETIME        DEFAULT NULL        COMMENT '锁定到期时间，NULL=未锁定',
  `updated_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_container_user` (`container_id`, `user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '加入密码尝试记录';

-- -------------------------------------------------------------
-- 6. 敏感操作日志
--
-- 只记「谁对谁做了什么」，不记业务数据快照：
--   查历史状态应该看业务表，日志只用来回答「这个管理员是谁设的」「这人是谁踢的」。
-- detail 里绝不能出现密码明文（Service 层写死文案，不透传入参）。
-- -------------------------------------------------------------
CREATE TABLE `t_container_op_log` (
  `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `container_id`   BIGINT UNSIGNED NOT NULL,
  `operator_id`    BIGINT UNSIGNED NOT NULL                COMMENT '操作人',
  `target_user_id` BIGINT UNSIGNED DEFAULT NULL            COMMENT '被操作人；改配置类为 NULL',
  `action_type`    VARCHAR(32)     NOT NULL                COMMENT 'SET_ADMIN/UNSET_ADMIN/MUTE/UNMUTE/REMOVE/DISSOLVE/UPDATE/REGEN_CODE/SET_PASSWORD/SET_EXPIRE/BLACKLIST/RELEASE_BLACKLIST/HIGHLIGHT/CANCEL_HIGHLIGHT',
  `detail`         VARCHAR(255)    DEFAULT NULL            COMMENT '人可读摘要，如「禁言 60 分钟：上课讲话」',
  `created_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_container_created` (`container_id`, `created_at`),
  KEY `idx_target` (`target_user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '容器敏感操作日志';

-- 核对：应该看到 6 行
-- 用 DATABASE() 而不是写死库名，这样在副本库上执行也能正确核对
SELECT TABLE_NAME, TABLE_COMMENT
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('t_subject_container', 't_container_member', 't_container_blacklist',
                     't_container_highlight', 't_container_join_attempt', 't_container_op_log')
ORDER BY TABLE_NAME;
