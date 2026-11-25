package com.banxing.classassistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.banxing.classassistant.entity.Score;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 成绩 Mapper
 *
 * <p>单表 CRUD 用 BaseMapper，两个自定义方法必须手写 SQL：
 * <ul>
 *   <li>{@link #batchUpsert} —— MyBatis-Plus 没有"存在就更新、不存在就插入"的批量方法</li>
 *   <li>{@link #selectClassAvg} —— 跨班级聚合，要 JOIN t_exam 和 t_class</li>
 * </ul>
 */
@Mapper
public interface ScoreMapper extends BaseMapper<Score> {

    /**
     * 批量写入成绩：同一个 (exam_id, student_id, subject) 已存在就更新分数，不存在就插入。
     *
     * <p>依赖唯一键 uk_exam_student_subject 才能触发 ON DUPLICATE KEY UPDATE。
     * 没有这个唯一键，MySQL 会当成普通 INSERT，同样的数据插两遍。
     *
     * <p>为什么不用"先 selectOne 再 insert/update"：
     * 141 条成绩就是 141 次查询 + 141 次写入，往返 282 次。
     * 这里一条 SQL 全部搞定。
     *
     * <p>score 用 jdbcType=DECIMAL 显式声明，因为它是可空的：
     * 缺考时传 null，不写 jdbcType 某些驱动会报 "无效的列类型"。
     */
    @Insert("""
            <script>
            INSERT INTO t_score (class_id, exam_id, student_id, subject, score, full_score)
            VALUES
            <foreach collection="rows" item="r" separator=",">
              (#{r.classId}, #{r.examId}, #{r.studentId}, #{r.subject},
               #{r.score,jdbcType=DECIMAL}, #{r.fullScore,jdbcType=DECIMAL})
            </foreach>
            ON DUPLICATE KEY UPDATE
              score      = VALUES(score),
              full_score = VALUES(full_score)
            </script>
            """)
    int batchUpsert(@Param("rows") List<Score> rows);

    /**
     * 同年级各班在这次考试上的"班级平均总分"。
     *
     * <p>为什么要用 name + examDate 而不是 examId 来匹配：
     * t_exam 是<b>按班</b>存的，1 班的"期中考试"是 id=2，2 班的"期中考试"是另一个 id。
     * 拿 examId 去查只能查到本班自己，年级排名永远第 1。
     * 所以要用「考试名 + 考试日期」这两个跨班一致的字段来对齐。
     *
     * <p>返回的每行形如 {classId: 1, avgTotal: 335.2}，
     * 按 avgTotal 降序，本班在第几个就是第几名。
     */
    @Select("""
            SELECT t.class_id AS classId, AVG(t.stu_total) AS avgTotal
            FROM (
                SELECT sc.class_id, sc.student_id, SUM(sc.score) AS stu_total
                FROM t_score sc
                JOIN t_exam  e ON e.id = sc.exam_id
                WHERE e.name = #{name}
                  AND e.exam_date = #{examDate}
                  AND e.class_id IN (SELECT id FROM t_class WHERE grade = #{grade})
                  AND sc.score IS NOT NULL
                GROUP BY sc.class_id, sc.student_id
            ) t
            GROUP BY t.class_id
            ORDER BY avgTotal DESC
            """)
    List<Map<String, Object>> selectClassAvg(@Param("name") String name,
                                             @Param("examDate") LocalDate examDate,
                                             @Param("grade") String grade);
}
