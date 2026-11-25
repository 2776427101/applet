-- =============================================================
-- 07 多班级改造：给每个班级补一个"班级群"
-- -------------------------------------------------------------
-- 背景：v0.5 起一个老师可以带多个班。通讯录里每个班都要有一个
--       "班级群"入口（群主 = 班主任），建班时由 ClassService 自动建。
--       本脚本负责把**已有的**班级补上群，不负责改表结构。
--
-- 为什么复用 t_subject_container 而不新建表：
--       它已经有 class_id 和 owner_id，一个班一个群正好是它的语义。
--       再建一张"班级群"表，聊天、成员、禁言、精华全要复制一遍。
--
-- 学生不会进群：t_student 没有 user_id / openid，学生还没有账号，
--       所以"学生归入班级"体现在通讯录分组（t_student.class_id）上，
--       而不是 t_container_member。这点不要当成 bug。
--
-- 幂等：可以重复执行。已有群的班会被跳过；群主行用 ON DUPLICATE KEY 兜底。
--
-- 用法（库名必须写在命令行，脚本里不要写 USE）：
--   mysql -uroot -p --default-character-set=utf8mb4 class_assistant < 07_class_group.sql
-- =============================================================

-- -------------------------------------------------------------
-- 1) 给每个还没有班级群的班级建一个群
-- -------------------------------------------------------------
-- 邀请码用 MD5 前 10 位，并把 0/1 换成 2/3：
--   InviteCodeGenerator 的字符集排除了 0/O/1/I（怕手抄看错），
--   后端校验格式时只认这 32 个字符。直接截 MD5 会得到含 0/1 的码，
--   用户能查到群，但 join 时会判"格式不合法"，所以必须替换掉。
USE `class_assistant`;

INSERT INTO `t_subject_container`
    (`subject_name`, `name`, `class_id`, `owner_id`, `invite_code`, `status`, `created_at`, `updated_at`)
SELECT c.`name`,
       c.`name`,
       c.`id`,
       c.`head_teacher_id`,
       UPPER(SUBSTRING(REPLACE(REPLACE(MD5(RAND()), '0', '2'), '1', '3'), 1, 10)),
       1,
       NOW(),
       NOW()
FROM `t_class` c
WHERE c.`status` = 1
  AND NOT EXISTS (
      SELECT 1
      FROM `t_subject_container` s
      WHERE s.`class_id` = c.`id`
        AND s.`status` = 1
  );

-- -------------------------------------------------------------
-- 2) 班主任 = 群主
-- -------------------------------------------------------------
-- join_source = 2 表示"建群时自动加入"（和 ClassService.ensureClassGroup 一致）
-- ON DUPLICATE KEY：老师可能早就在这个群里（role=3），这里顺手提成群主
INSERT INTO `t_container_member`
    (`container_id`, `user_id`, `role`, `status`, `join_source`, `joined_at`, `created_at`, `updated_at`)
SELECT s.`id`, s.`owner_id`, 1, 1, 2, NOW(), NOW(), NOW()
FROM `t_subject_container` s
JOIN `t_class` c ON c.`id` = s.`class_id`
WHERE s.`status` = 1
ON DUPLICATE KEY UPDATE `role` = 1, `status` = 1;

-- -------------------------------------------------------------
-- 3) 校验：每个班都应该有群，且群主行恰好 1 条
-- -------------------------------------------------------------
SELECT c.`id`                                  AS class_id,
       c.`name`                                AS class_name,
       s.`id`                                  AS container_id,
       s.`name`                                AS container_name,
       (SELECT COUNT(*)
        FROM `t_container_member` m
        WHERE m.`container_id` = s.`id`
          AND m.`role` = 1)                    AS owner_rows,
       (SELECT COUNT(*)
        FROM `t_student` st
        WHERE st.`class_id` = c.`id`
          AND st.`status` = 1)                 AS student_count
FROM `t_class` c
LEFT JOIN `t_subject_container` s
       ON s.`class_id` = c.`id` AND s.`status` = 1
WHERE c.`status` = 1
ORDER BY c.`id`;
