package com.banxing.classassistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.banxing.classassistant.dto.DirectThreadRow;
import com.banxing.classassistant.entity.DirectMessage;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 师生私信表数据访问接口
 */
public interface DirectMessageMapper extends BaseMapper<DirectMessage> {

    /**
     * 我的私信会话列表：每个学生一行，按最近一条消息倒序。
     *
     * <p>{@code SUM(CASE WHEN ...)} 而不是再查一次 count：
     * 未读数和最后一条消息本来就来自同一批行，一次扫完比两次查询省事，
     * 也不会出现"列表显示 3 条未读、点进去只有 2 条"这种不一致。
     *
     * <p>{@code classIds} 为空时不要在 SQL 里拼空 IN —— 调用方先判空返回空列表。
     */
    @Select("<script>"
            + "SELECT student_id AS studentId,"
            + "       MAX(id) AS lastId,"
            + "       SUM(CASE WHEN sender_role = 2 AND read_at IS NULL THEN 1 ELSE 0 END) AS unread"
            + "  FROM t_direct_message"
            + " WHERE class_id IN"
            + "   <foreach collection='classIds' item='c' open='(' separator=',' close=')'>#{c}</foreach>"
            + " GROUP BY student_id"
            + " ORDER BY MAX(id) DESC"
            + " LIMIT #{limit}"
            + "</script>")
    List<DirectThreadRow> selectThreads(@Param("classIds") List<Long> classIds,
                                        @Param("limit") int limit);
}
