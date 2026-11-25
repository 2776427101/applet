-- =============================================================
-- 10_remap_orphan_student_id.sql
-- 把指向「幽灵学生」的成绩 / 请假 / 座位重新挂到真实学生身上
-- =============================================================
--
-- 问题：t_score(282 行) / t_leave_request(4 行) / t_seat(47 行) 里的 student_id
-- 是 1~47，而 t_student 里根本没有 id<=47 的人。一班真实学生的 id 是 121~177。
-- 结果：成绩页、请假页、座位表里的学生名全显示「未知」，乐迪也只能回「没有记录」。
--
-- 原因：种子数据是按一份 47 人的旧名单生成的，那份名单后来被导入的真实名单
-- （57 人）替换掉了，但引用了它的三张表没跟着改。
--
-- 映射规则：第 k 个幽灵学生 → 班里第 k 个学生（按 id 升序）。
--
-- 为什么只能位置映射：成绩行里只有科目和分数，没有任何能反查身份的东西，
-- 原始对应关系已经不可恢复。位置映射至少保证「一个幽灵只对应一个真人」，
-- 而且和座位的排法一致（座位 1 = 名单第 1 人），故事是自洽的。
--
-- 副作用：121~167 这 47 人会有成绩，168~177 这 10 人没有。
-- 这是正常现象（插班生不参加上次考试），不是 bug。
--
-- 执行前必须先备份：
--   mysqldump ... class_assistant t_score t_leave_request t_seat \
--     > database/backups/2026-11-18_before_remap.sql
-- =============================================================

USE `class_assistant`;

START TRANSACTION;

CREATE TEMPORARY TABLE tmp_remap (
  old_id BIGINT UNSIGNED NOT NULL PRIMARY KEY,
  new_id BIGINT UNSIGNED NOT NULL,
  UNIQUE KEY uk_new (new_id)
);

-- 三张表里出现过的幽灵 id 合在一起排序，第 k 个配给班里第 k 个学生
INSERT INTO tmp_remap (old_id, new_id)
SELECT p.old_id, s.new_id
FROM (
  SELECT d.old_id, ROW_NUMBER() OVER (ORDER BY d.old_id) AS rn
  FROM (
    SELECT DISTINCT student_id AS old_id FROM t_score
    UNION
    SELECT DISTINCT student_id FROM t_leave_request
    UNION
    SELECT DISTINCT student_id FROM t_seat WHERE student_id IS NOT NULL
  ) d
) p
JOIN (
  SELECT r.new_id, ROW_NUMBER() OVER (ORDER BY r.new_id) AS rn
  FROM (SELECT id AS new_id FROM t_student WHERE class_id = 1 AND status = 1) r
) s ON s.rn = p.rn;

-- 执行前看一眼映射表（旧 id -> 新 id + 姓名）
SELECT m.old_id, m.new_id, st.name, st.student_no
FROM tmp_remap m JOIN t_student st ON st.id = m.new_id
ORDER BY m.old_id;

UPDATE t_score sc         JOIN tmp_remap m ON m.old_id = sc.student_id SET sc.student_id = m.new_id;
UPDATE t_leave_request lr JOIN tmp_remap m ON m.old_id = lr.student_id SET lr.student_id = m.new_id;
UPDATE t_seat st          JOIN tmp_remap m ON m.old_id = st.student_id SET st.student_id = m.new_id;

-- ---------- 自检 ----------
-- 1) 还剩多少孤儿行（必须是 0 / 0 / 0）
SELECT '孤儿行数' AS chk,
       (SELECT COUNT(*) FROM t_score sc LEFT JOIN t_student st ON st.id = sc.student_id WHERE st.id IS NULL)         AS score_left,
       (SELECT COUNT(*) FROM t_leave_request lr LEFT JOIN t_student st ON st.id = lr.student_id WHERE st.id IS NULL) AS leave_left,
       (SELECT COUNT(*) FROM t_seat s LEFT JOIN t_student st ON st.id = s.student_id
         WHERE s.student_id IS NOT NULL AND st.id IS NULL)                                                          AS seat_left;

-- 2) 行数必须和改之前一样（282 / 4 / 48）
SELECT '行数' AS chk,
       (SELECT COUNT(*) FROM t_score)         AS score_rows,
       (SELECT COUNT(*) FROM t_leave_request) AS leave_rows,
       (SELECT COUNT(*) FROM t_seat)          AS seat_rows;

-- 3) 成绩能不能挂到班上的学生（每人应 6 行 = 2 场考试 × 3 科）
SELECT st.name, COUNT(*) AS rows_, COUNT(DISTINCT sc.exam_id) AS exams
FROM t_score sc JOIN t_student st ON st.id = sc.student_id
GROUP BY st.id, st.name
ORDER BY st.id
LIMIT 5;

COMMIT;

-- 回滚办法：mysql ... class_assistant < database/backups/2026-11-18_before_remap.sql
