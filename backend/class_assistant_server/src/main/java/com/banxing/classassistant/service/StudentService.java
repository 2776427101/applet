package com.banxing.classassistant.service;
import com.banxing.classassistant.dto.StudentDetailVO;
import com.banxing.classassistant.dto.StudentListVO;
import com.banxing.classassistant.dto.StudentSaveDTO;
/**
 * 学生业务
 */
public interface StudentService {
    /**
     * 查本班学生列表
     *
     * @param userId  当前登录老师 ID（Controller 从 request 里取出来传进来）
     * @param keyword 姓名或学号模糊搜索，可为 null
     * @param status  1=在读 0=已离班 -1=全部，可为 null（默认按 1 处理）
     * @return total + list
     */
    StudentListVO list(Long userId, String keyword, Integer status);

    /**
     * 查一个学生的详情：基本信息 + 座位 + 请假统计 + 最近一次成绩
     *
     * @param userId    当前登录老师 ID
     * @param studentId 学生 ID（前端传的，必须校验是不是本班的）
     * @return 详情，seat / leave / latestScore 三块可能为 null
     */
    StudentDetailVO detail(Long userId, Long studentId);

    /**
     * 新增学生
     *
     * @param userId 当前登录老师 ID
     * @param dto    姓名 / 性别 / 生日 / 学号
     * @return 新学生的 id
     */
    Long create(Long userId, StudentSaveDTO dto);

    /**
     * 编辑学生（全量提交，没传的字段会被清空）
     *
     * @param userId    当前登录老师 ID
     * @param studentId 要改的学生
     * @param dto       改完之后的完整数据
     */
    void update(Long userId, Long studentId, StudentSaveDTO dto);

    /**
     * 移出班级（软删除：status 改成 0，不真删）
     *
     * <p>为什么不真删：请假记录、成绩记录都还指着这个 student_id，
     * 真删了历史数据就成了孤儿，查「上学期的请假」会查出 student_name = null。
     *
     * @param userId    当前登录老师 ID
     * @param studentId 要移出的学生
     */
    void remove(Long userId, Long studentId);
}
