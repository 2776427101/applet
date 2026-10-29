package com.banxing.classassistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.banxing.classassistant.common.BizException;
import com.banxing.classassistant.dto.LeaveAuditDTO;
import com.banxing.classassistant.dto.LeaveCreateDTO;
import com.banxing.classassistant.dto.LeaveListVO;
import com.banxing.classassistant.dto.LeaveVO;
import com.banxing.classassistant.entity.LeaveRequest;
import com.banxing.classassistant.entity.Student;
import com.banxing.classassistant.mapper.LeaveRequestMapper;
import com.banxing.classassistant.mapper.StudentMapper;
import com.banxing.classassistant.service.ClassService;
import com.banxing.classassistant.service.LeaveService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 请假业务实现
 *
 * 这个模块比前两个难的地方只有一个：状态机。
 * 一条记录只能 0 → 1 或 0 → 2，不能倒回去，也不能 1 → 2。
 * 所有"能不能改"的判断都集中在 audit 方法里，别散到 Controller 或前端。
 */
@Service
@RequiredArgsConstructor
public class LeaveServiceImpl implements LeaveService {

    private final ClassService classService;
    private final StudentMapper studentMapper;
    private final LeaveRequestMapper leaveRequestMapper;

    // ==================== 列表 ====================

    @Override
    public LeaveListVO list(Long userId, Integer status, Integer page, Integer size) {
        classService.requireTeacher(userId);
        Long classId = classService.myClassId(userId);

        // 分页参数先钳制，再使用。
        // 不钳制的话前端传 size=999999 就能一次拉全表，等于把分页废掉了
        int p = (page == null || page < 1) ? 1 : page;
        int s = (size == null || size < 1 || size > 100) ? 20 : size;

        if (classId == null) {
            return new LeaveListVO(0, p, s, Collections.emptyList());
        }

        LambdaQueryWrapper<LeaveRequest> qw = new LambdaQueryWrapper<>();

        // 班级条件：漏了就能看到别的班的请假
        qw.eq(LeaveRequest::getClassId, classId);

        // 状态条件：-1 表示全部，不加条件；0/1/2/3 才加
        if (status != null && status != -1) {
            qw.eq(LeaveRequest::getStatus, status);
        }

        // 先数总数。注意这一步必须在 last() 之前做 ——
        // last() 是往 wrapper 上"贴"字符串的，贴完再数会变成 COUNT(*) ... LIMIT，
        // 语法直接报错
        long total = leaveRequestMapper.selectCount(qw);
        if (total == 0) {
            return new LeaveListVO(0, p, s, Collections.emptyList());
        }

        // 分页。
        // 这里没用 MyBatis-Plus 的 Page + PaginationInnerInterceptor，
        // 因为 3.5.9 之后分页插件被拆到了 mybatis-plus-jsqlparser 这个独立依赖里，
        // 项目现在没有这个依赖。手写 LIMIT 只有 3 行，先这么办。
        //
        // 安全说明：offset 和 s 都是 int，进 SQL 之前已经被上面的钳制限制在
        // 0 ~ 100 之间，拼不出任何非数字内容，所以不存在注入。
        int offset = (p - 1) * s;
        qw.last("ORDER BY status = 0 DESC, created_at DESC LIMIT " + offset + "," + s);

        List<LeaveRequest> rows = leaveRequestMapper.selectList(qw);

        // 批量补学生姓名。
        //
        // 为什么不 JOIN：JOIN 要写 XML 或 @Select 注解，动态条件还得自己拼；
        // 而这里先用单表查询拿到 20 条，再按 id 批量查一次学生，一共 2 条 SQL，
        // 代码全在 Java 里，好读也好改。数据量大了再换 JOIN 也不迟。
        //
        // ⚠️ 千万别在循环里 selectById —— 20 条记录就是 20 次查询，这叫 N+1 问题
        Map<Long, String> nameMap = new HashMap<>();
        Set<Long> studentIds = rows.stream()
                .map(LeaveRequest::getStudentId)
                .collect(Collectors.toSet());
        if (!studentIds.isEmpty()) {
            List<Student> students = studentMapper.selectList(
                    new LambdaQueryWrapper<Student>().in(Student::getId, studentIds));
            for (Student st : students) {
                nameMap.put(st.getId(), st.getName());
            }
        }

        List<LeaveVO> list = rows.stream()
                .map(r -> LeaveVO.from(r, nameMap.get(r.getStudentId())))
                .toList();

        return new LeaveListVO(total, p, s, list);
    }

    // ==================== 审批 ====================

    @Override
    public void audit(Long userId, Long id, LeaveAuditDTO dto) {
        classService.requireTeacher(userId);
        Long classId = classService.myClassId(userId);
        if (classId == null) {
            throw new BizException(1001, "请先绑定班级");
        }

        Integer target = dto.getStatus();
        if (target == null || (target != 1 && target != 2)) {
            throw new BizException(1001, "只能通过(1)或驳回(2)");
        }

        String remark = dto.getRemark() == null ? null : dto.getRemark().trim();
        if (remark != null && remark.length() > 255) {
            throw new BizException(1001, "审批备注不能超过 255 字");
        }

        LeaveRequest exist = leaveRequestMapper.selectById(id);
        if (exist == null) {
            throw new BizException(1004, "请假记录不存在");
        }
        // 请假表没有 teacher_id，只能靠 class_id 判断归属
        if (!exist.getClassId().equals(classId)) {
            throw new BizException(3002, "这条请假不属于你的班");
        }

        // ★ 状态机就这一行。
        // 只有"待审批(0)"能往下走。已经通过的不许再驳回，
        // 否则数据会乱：家长看到的是"已通过"，数据库里却是"已驳回"
        if (exist.getStatus() == null || exist.getStatus() != 0) {
            throw new BizException(1001, "该请假已处理过");
        }

        // 瘦对象更新：只写 status 和 audit_remark 两列
        LeaveRequest update = new LeaveRequest();
        update.setId(id);
        update.setStatus(target);
        if (remark != null && !remark.isEmpty()) {
            update.setAuditRemark(remark);
        }
        leaveRequestMapper.updateById(update);
    }

    // ==================== 代学生请假 ====================

    @Override
    public Long add(Long userId, LeaveCreateDTO dto) {
        classService.requireTeacher(userId);
        Long classId = classService.myClassId(userId);
        if (classId == null) {
            throw new BizException(1001, "请先绑定班级");
        }

        if (dto.getStudentId() == null) {
            throw new BizException(1001, "请选择学生");
        }
        if (dto.getType() == null || dto.getType() < 1 || dto.getType() > 3) {
            throw new BizException(1001, "请假类型只能是 1(病假)/2(事假)/3(其他)");
        }
        if (dto.getStartAt() == null || dto.getEndAt() == null) {
            throw new BizException(1001, "请填写请假起止时间");
        }
        // isAfter 是"严格晚于"。相等也不行 —— 零时长的请假没有意义
        if (!dto.getEndAt().isAfter(dto.getStartAt())) {
            throw new BizException(1001, "结束时间必须晚于开始时间");
        }

        String reason = dto.getReason() == null ? null : dto.getReason().trim();
        if (reason != null && reason.length() > 255) {
            throw new BizException(1001, "请假原因不能超过 255 字");
        }

        // 学生必须存在，且必须是自己班的。
        // 只校验"存在"不够：前端把 studentId 改成别班的 id，一样能插进去
        Student student = studentMapper.selectById(dto.getStudentId());
        if (student == null) {
            throw new BizException(3002, "学生不存在");
        }
        if (!student.getClassId().equals(classId)) {
            throw new BizException(3002, "该学生不属于你的班");
        }
        if (student.getStatus() != null && student.getStatus() == 0) {
            throw new BizException(1001, "该学生已离班，不能登记请假");
        }

        LeaveRequest lr = new LeaveRequest();
        lr.setClassId(classId);
        lr.setStudentId(student.getId());
        lr.setType(dto.getType());
        lr.setStartAt(dto.getStartAt());
        lr.setEndAt(dto.getEndAt());
        lr.setReason(reason == null || reason.isEmpty() ? null : reason);

        // ★ 老师代录的一律是"已通过"。
        // 家长在微信里跟老师请过假了，老师才来后台补录，
        // 这时候再走一遍审批流程就是自己批自己
        lr.setStatus(1);

        leaveRequestMapper.insert(lr);
        return lr.getId();
    }
}
