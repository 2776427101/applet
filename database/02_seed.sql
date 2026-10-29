-- =============================================================
-- 班级助手 · 测试数据 v0.1
-- 用途：本地跑通首页，让 4 个数字都不是 0
-- 依赖：先执行 01_schema.sql
-- 注意：这是假数据，别在生产库跑
-- =============================================================

USE `class_assistant`;

-- 1. 一个老师
INSERT INTO `t_user` (`openid`, `role`, `name`, `phone`, `status`)
VALUES ('seed_openid_teacher', 1, '李光', '13800000000', 1);

-- 2. 一个班：term_start_date 取"今天 - 49 天"，正好落在第 8 周
INSERT INTO `t_class` (`name`, `grade`, `head_teacher_id`, `term_start_date`, `invite_code`)
VALUES ('628班', '高一', 1, DATE_SUB(CURDATE(), INTERVAL 49 DAY), '628628');

-- 3. 47 个学生（用递归生成，省得手写 47 行；需要 MySQL 8.0+）
INSERT INTO `t_student` (`class_id`, `name`, `gender`, `birthday`, `student_no`, `status`)
WITH RECURSIVE `seq` (`n`) AS (
  SELECT 1
  UNION ALL
  SELECT `n` + 1 FROM `seq` WHERE `n` < 47
)
SELECT 1,
       CONCAT('学生', LPAD(`n`, 2, '0')),
       IF(`n` % 2 = 0, 1, 2),
       DATE_ADD('2009-03-01', INTERVAL (`n` * 9) DAY),
       CONCAT('2026', LPAD(`n`, 3, '0')),
       1
FROM `seq`;

-- 4. 让 3 号学生的生日 = 今天（首页"今日生日"会变成 1）
UPDATE `t_student`
SET `birthday` = DATE_ADD('2009-01-01', INTERVAL (DAYOFYEAR(CURDATE()) - 1) DAY)
WHERE `id` = 3;

-- 5. 今天一条已通过的请假（首页"今日请假"= 1）
INSERT INTO `t_leave_request` (`class_id`, `student_id`, `type`, `start_at`, `end_at`, `reason`, `status`)
VALUES (1, 2, 1,
        CONCAT(CURDATE(), ' 08:00:00'),
        CONCAT(CURDATE(), ' 18:00:00'),
        '发烧就医', 1);

-- 5.1 再补三条，让请假页的「待审批」和「已处理」两个 tab 都有东西看
--     ⚠️ 库里已经有数据了，就只跑这一段 INSERT，别整个重跑本文件，会重复插入
INSERT INTO `t_leave_request`
  (`class_id`, `student_id`, `type`, `start_at`, `end_at`, `reason`, `status`, `audit_remark`)
VALUES
  (1, 5, 1,
   CONCAT(DATE_ADD(CURDATE(), INTERVAL 1 DAY), ' 08:00:00'),
   CONCAT(DATE_ADD(CURDATE(), INTERVAL 3 DAY), ' 18:00:00'),
   '扁桃体发炎，医生建议在家休息三天', 0, NULL),
  (1, 9, 2,
   CONCAT(DATE_SUB(CURDATE(), INTERVAL 2 DAY), ' 08:00:00'),
   CONCAT(DATE_SUB(CURDATE(), INTERVAL 1 DAY), ' 18:00:00'),
   '家里有事，需回老家一趟', 0, NULL),
  (1, 12, 3,
   CONCAT(DATE_SUB(CURDATE(), INTERVAL 6 DAY), ' 13:00:00'),
   CONCAT(DATE_SUB(CURDATE(), INTERVAL 6 DAY), ' 17:30:00'),
   '参加市级数学竞赛', 2, '比赛通知没交上来，先驳回，补了再批');

-- 6. 两条待办，一条未完成（首页"待办未完成"= 1）
INSERT INTO `t_todo` (`teacher_id`, `class_id`, `title`, `done`, `due_at`)
VALUES
  (1, 1, '收齐本周家长回执', 0, CONCAT(CURDATE(), ' 20:00:00')),
  (1, 1, '整理月考成绩单',   1, CONCAT(CURDATE(), ' 18:00:00'));

-- 7. 自检：跑一下这三条，确认数字和首页对得上
-- SELECT COUNT(*) FROM t_student WHERE class_id = 1 AND status = 1;              -- 应为 47
-- SELECT COUNT(*) FROM t_student WHERE class_id = 1 AND status = 1
--   AND MONTH(birthday) = MONTH(CURDATE()) AND DAY(birthday) = DAY(CURDATE());    -- 应为 1
-- SELECT COUNT(*) FROM t_leave_request WHERE class_id = 1 AND status = 1
--   AND DATE(start_at) = CURDATE();                                              -- 应为 1

-- =============================================================
-- 8. 成绩模块演示数据（2 次考试 × 47 人 × 3 科）
--    分数用 id 套公式生成，保证每次跑结果一样（真随机的话
--    你重装一次库截图就变了，没法对照教程）
-- =============================================================
INSERT INTO `t_exam` (`class_id`, `name`, `type`, `exam_date`, `subjects`, `full_score`)
VALUES (1, '第一次月考', 2, DATE_SUB(CURDATE(), INTERVAL 29 DAY),
        '语文,数学,英语', '{"语文":150,"数学":150,"英语":150}');
SET @exam1 = LAST_INSERT_ID();

INSERT INTO `t_exam` (`class_id`, `name`, `type`, `exam_date`, `subjects`, `full_score`)
VALUES (1, '期中考试', 3, DATE_SUB(CURDATE(), INTERVAL 7 DAY),
        '语文,数学,英语', '{"语文":150,"数学":150,"英语":150}');
SET @exam2 = LAST_INSERT_ID();

-- 第一次月考
INSERT INTO `t_score` (`class_id`, `exam_id`, `student_id`, `subject`, `score`, `full_score`)
SELECT 1, @exam1, id, '语文', ROUND(92 + MOD(id * 37, 50) + MOD(id * 7, 10) / 10, 1), 150
FROM `t_student` WHERE class_id = 1 AND status = 1;

INSERT INTO `t_score` (`class_id`, `exam_id`, `student_id`, `subject`, `score`, `full_score`)
SELECT 1, @exam1, id, '数学', ROUND(88 + MOD(id * 53, 56) + MOD(id * 3, 10) / 10, 1), 150
FROM `t_student` WHERE class_id = 1 AND status = 1;

INSERT INTO `t_score` (`class_id`, `exam_id`, `student_id`, `subject`, `score`, `full_score`)
SELECT 1, @exam1, id, '英语', ROUND(95 + MOD(id * 29, 48) + MOD(id * 9, 10) / 10, 1), 150
FROM `t_student` WHERE class_id = 1 AND status = 1;

-- 期中考试（系数换一组，让排名和上次不一样，看得出变化）
INSERT INTO `t_score` (`class_id`, `exam_id`, `student_id`, `subject`, `score`, `full_score`)
SELECT 1, @exam2, id, '语文', ROUND(90 + MOD(id * 41, 54) + MOD(id * 3, 10) / 10, 1), 150
FROM `t_student` WHERE class_id = 1 AND status = 1;

INSERT INTO `t_score` (`class_id`, `exam_id`, `student_id`, `subject`, `score`, `full_score`)
SELECT 1, @exam2, id, '数学', ROUND(85 + MOD(id * 47, 60) + MOD(id * 9, 10) / 10, 1), 150
FROM `t_student` WHERE class_id = 1 AND status = 1;

INSERT INTO `t_score` (`class_id`, `exam_id`, `student_id`, `subject`, `score`, `full_score`)
SELECT 1, @exam2, id, '英语', ROUND(93 + MOD(id * 31, 52) + MOD(id * 7, 10) / 10, 1), 150
FROM `t_student` WHERE class_id = 1 AND status = 1;

-- 造一个缺考：期中考试学生 7 的数学没考（score = NULL，不是 0）
UPDATE `t_score` SET `score` = NULL
WHERE `exam_id` = @exam2 AND `student_id` = 7 AND `subject` = '数学';

-- 自检
-- SELECT e.id, e.name, COUNT(s.id) AS 条数 FROM t_exam e
--   LEFT JOIN t_score s ON s.exam_id = e.id GROUP BY e.id;
--   -- 应为 第一次月考 141、期中考试 140（少一条是缺考那条被 NULL 掉了，但行还在）
-- SELECT COUNT(*) FROM t_score WHERE score IS NULL;   -- 应为 1

-- =============================================================
-- 9. 座位模块演示数据：6 排 × 8 列 = 48 个格子，47 个学生，最后一格空着
--
-- 空座位也要存一行（student_id = NULL），否则后端没法区分
-- "6 排 × 8 列但最后一格没人" 和 "只有 47 个座位"。
-- =============================================================
INSERT INTO `t_seat` (`class_id`, `row_no`, `col_no`, `student_id`)
WITH RECURSIVE `cell` (`n`) AS (
  SELECT 1
  UNION ALL
  SELECT `n` + 1 FROM `cell` WHERE `n` < 48
),
`ranked` AS (
  -- 按学号排个序，第 n 个学生坐第 n 个格子
  SELECT `id`, ROW_NUMBER() OVER (ORDER BY `student_no`, `id`) AS `rn`
  FROM `t_student` WHERE `class_id` = 1 AND `status` = 1
)
SELECT 1,
       CEIL(`cell`.`n` / 8),                    -- 第几排：1~6
       ((`cell`.`n` - 1) % 8) + 1,              -- 第几列：1~8
       `ranked`.`id`                            -- 第 48 格没有学生 → NULL
FROM `cell`
LEFT JOIN `ranked` ON `ranked`.`rn` = `cell`.`n`;

-- 自检
-- SELECT row_no, COUNT(*) AS 格子数, COUNT(student_id) AS 有人数
--   FROM t_seat WHERE class_id = 1 GROUP BY row_no;
--   -- 应为 6 行 × 8 格，前 5 排 8 人、第 6 排 7 人
