-- =============================================================
-- 11_aidi_memo.sql
-- 乐迪的长期记忆（事实层）
-- =============================================================
--
-- 注意这里存的是「稳定事实」，不是对话。
--   ✔ 周三下午没课 / 录入成绩默认用最近一次考试 / 班费由班长收
--   ✘ 张三考了多少分（这是数据，每次现查）
--   ✘ 用户上一句问了什么（这是对话，存在前端）
--
-- 为什么不存对话：对话存下来就要清理、要处理"换手机看不到历史"，
-- 更麻烦的是模型会把上周的答案当事实端出来——学生都转走了它还在念旧数据。
-- 事实层没有这个问题：记的是"规则"，不是"结论"。
--
-- class_id = 0 表示跨班通用（例如"我姓李，别叫我老师"）。
-- 一个老师带多个班时，班内的事实不会串到别的班去。

USE `class_assistant`;

CREATE TABLE IF NOT EXISTS t_aidi_memo (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id    BIGINT UNSIGNED NOT NULL                COMMENT '所属老师',
  class_id   BIGINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '所属班级；0=跨班通用',
  content    VARCHAR(120)    NOT NULL                COMMENT '一条事实，一句话说清',
  status     TINYINT         NOT NULL DEFAULT 1      COMMENT '1=有效 0=已忘掉',
  created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  -- 同一句话记两遍没有意义。status 不放进唯一键：
  -- 忘掉再重新记一次，应该复活原来那行，而不是再插一条
  UNIQUE KEY uk_user_class_content (user_id, class_id, content),
  KEY idx_user_status (user_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='乐迪的长期记忆（事实层）';
