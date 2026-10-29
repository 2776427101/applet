package com.banxing.classassistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.banxing.classassistant.entity.Seat;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 座位 Mapper
 *
 * <p>只有批量写入需要手写，其余（按班查、按班删）用 BaseMapper 的
 * selectList / delete 就够了，条件是 LambdaQueryWrapper。
 */
@Mapper
public interface SeatMapper extends BaseMapper<Seat> {

    /**
     * 一条 SQL 写入整张座位图。
     *
     * <p>为什么不循环 insert：6×8 是 48 次往返，虽然本地跑感觉不出来，
     * 但这是"整图覆盖保存"里唯一一处会随教室变大而线性变慢的地方。
     * 一条 INSERT ... VALUES (...),(...),(...) 只有 1 次往返。
     *
     * <p>student_id 可以为空，必须显式写 jdbcType=BIGINT ——
     * 不写的话某些驱动遇到 null 会抛"无效的列类型"。
     * 这和成绩批量写入里 score 要写 jdbcType=DECIMAL 是同一个坑。
     *
     * <p>调用方保证 rows 不为空：空列表会拼出 `VALUES` 后面什么都没有的语法错误。
     */
    @Insert("""
            <script>
            INSERT INTO t_seat (class_id, row_no, col_no, student_id)
            VALUES
            <foreach collection="rows" item="s" separator=",">
              (#{s.classId}, #{s.rowNo}, #{s.colNo}, #{s.studentId,jdbcType=BIGINT})
            </foreach>
            </script>
            """)
    int batchInsert(@Param("rows") List<Seat> rows);
}
