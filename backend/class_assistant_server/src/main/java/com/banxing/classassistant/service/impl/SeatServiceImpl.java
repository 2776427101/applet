package com.banxing.classassistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.banxing.classassistant.common.BizException;
import com.banxing.classassistant.dto.SeatChartVO;
import com.banxing.classassistant.dto.SeatSaveDTO;
import com.banxing.classassistant.dto.SeatStudentVO;
import com.banxing.classassistant.dto.SeatVO;
import com.banxing.classassistant.entity.Seat;
import com.banxing.classassistant.entity.Student;
import com.banxing.classassistant.mapper.SeatMapper;
import com.banxing.classassistant.mapper.StudentMapper;
import com.banxing.classassistant.service.ClassService;
import com.banxing.classassistant.service.SeatService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 座位业务实现
 *
 * <p>这个模块的难点不在 SQL，在两件事：
 * <ol>
 *   <li><b>空座位也存一行</b> —— 否则 rows/cols 只能靠 max() 反推，
 *       最后一排没人时排数会凭空少一排</li>
 *   <li><b>整图覆盖要"先校验、后删除"</b> —— 校验放在 DELETE 后面的话，
 *       数据有问题时座位已经被清空了，老师打开页面看到一张白纸</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class SeatServiceImpl implements SeatService {

    /** 没排过座时的默认教室：6 排 8 列（和设计稿一致） */
    private static final int DEFAULT_ROWS = 6;
    private static final int DEFAULT_COLS = 8;

    /** 行列数上限。20×20=400 格，再多手机上就点不准了 */
    private static final int MAX_SIZE = 20;

    private final ClassService classService;
    private final SeatMapper seatMapper;
    private final StudentMapper studentMapper;

    // ==================== 读 ====================

    @Override
    public SeatChartVO chart(Long userId) {
        classService.requireTeacher(userId);
        Long classId = classService.myClassId(userId);

        // 没绑班不是错误，返回一张空白教室，前端能正常显示"6 排 × 8 列，一个人都没有"
        if (classId == null) {
            return new SeatChartVO(DEFAULT_ROWS, DEFAULT_COLS,
                    Collections.emptyList(), Collections.emptyList());
        }

        List<Seat> rows = seatMapper.selectList(new LambdaQueryWrapper<Seat>()
                .eq(Seat::getClassId, classId)
                .orderByAsc(Seat::getRowNo)
                .orderByAsc(Seat::getColNo));

        // 行列数直接由格子反推。因为空格子也存了行，
        // max(row_no) 就是老师上次设的排数，不会因为最后一排没人而缩水
        int rowCount = DEFAULT_ROWS;
        int colCount = DEFAULT_COLS;
        if (!rows.isEmpty()) {
            rowCount = rows.stream().mapToInt(Seat::getRowNo).max().orElse(DEFAULT_ROWS);
            colCount = rows.stream().mapToInt(Seat::getColNo).max().orElse(DEFAULT_COLS);
        }

        // 一次把用到的学生查出来，别在循环里 selectById（N+1）
        Set<Long> seatedIds = rows.stream()
                .map(Seat::getStudentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, Student> studentMap = new HashMap<>();
        if (!seatedIds.isEmpty()) {
            List<Student> students = studentMapper.selectList(
                    new LambdaQueryWrapper<Student>().in(Student::getId, seatedIds));
            for (Student s : students) {
                studentMap.put(s.getId(), s);
            }
        }

        List<SeatVO> seats = new ArrayList<>(rows.size());
        for (Seat s : rows) {
            Student stu = s.getStudentId() == null ? null : studentMap.get(s.getStudentId());

            // 座位指向的学生必须"存在 + 本班 + 在读"，三条缺一条就当空位。
            // 学生离班后座位记录不会自动删，不判这一下，离班学生的名字会一直挂在座位上
            boolean ok = stu != null
                    && classId.equals(stu.getClassId())
                    && (stu.getStatus() == null || stu.getStatus() == 1);

            seats.add(ok
                    ? new SeatVO(s.getRowNo(), s.getColNo(), stu.getId(), stu.getName(), stu.getGender())
                    : new SeatVO(s.getRowNo(), s.getColNo(), null, null, null));
        }

        // 未排座 = 本班在读学生 - 已经坐在座位上的
        List<Student> all = studentMapper.selectList(new LambdaQueryWrapper<Student>()
                .eq(Student::getClassId, classId)
                .eq(Student::getStatus, 1)
                .last("ORDER BY student_no IS NULL, student_no, id"));

        List<SeatStudentVO> unseated = all.stream()
                .filter(s -> !seatedIds.contains(s.getId()))
                .map(s -> new SeatStudentVO(s.getId(), s.getName(), s.getGender()))
                .toList();

        return new SeatChartVO(rowCount, colCount, seats, unseated);
    }

    // ==================== 写 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int save(Long userId, SeatSaveDTO dto) {
        classService.requireTeacher(userId);
        Long classId = classService.myClassId(userId);
        if (classId == null) {
            throw new BizException(1001, "请先绑定班级");
        }

        if (dto == null || dto.getRows() == null || dto.getCols() == null) {
            throw new BizException(1001, "请提供行列数");
        }
        int rows = dto.getRows();
        int cols = dto.getCols();
        if (rows < 1 || rows > MAX_SIZE || cols < 1 || cols > MAX_SIZE) {
            throw new BizException(1001, "行列数必须在 1~" + MAX_SIZE + " 之间");
        }
        if (dto.getSeats() == null) {
            throw new BizException(1001, "缺少 seats 字段");
        }

        // 用 1-based 的二维数组，下标就是"第几排第几列"，省得到处 ±1 写出 off-by-one
        Long[][] grid = new Long[rows + 1][cols + 1];
        boolean[][] taken = new boolean[rows + 1][cols + 1];

        // ---- 第一遍：只做与数据库无关的校验，把图铺进内存 ----
        for (SeatSaveDTO.Item it : dto.getSeats()) {
            if (it == null || it.getRow() == null || it.getCol() == null) {
                throw new BizException(1001, "座位缺少 row 或 col");
            }
            int r = it.getRow();
            int c = it.getCol();
            if (r < 1 || r > rows || c < 1 || c > cols) {
                throw new BizException(1001, "第 " + r + " 排第 " + c + " 列 超出 " + rows + "×" + cols);
            }
            // taken 和 grid 分开：空格子的 grid 是 null，只靠 grid 判不出
            // "同一个格子先传了 null、又传了学生"这种情况
            if (taken[r][c]) {
                throw new BizException(1001, "第 " + r + " 排第 " + c + " 列 出现了两次");
            }
            taken[r][c] = true;
            grid[r][c] = it.getStudentId();
        }

        // ---- 第二遍：查学生，校验归属和"一人两座" ----
        Map<Long, int[]> firstPos = new HashMap<>();
        List<Long> dupStudent = new ArrayList<>();
        Map<Long, int[]> dupPos = new HashMap<>();

        for (int r = 1; r <= rows; r++) {
            for (int c = 1; c <= cols; c++) {
                Long sid = grid[r][c];
                if (sid == null) {
                    continue;
                }
                int[] prev = firstPos.putIfAbsent(sid, new int[]{r, c});
                if (prev != null) {
                    dupStudent.add(sid);
                    dupPos.putIfAbsent(sid, prev);
                }
            }
        }

        Set<Long> ids = new HashSet<>(firstPos.keySet());
        Map<Long, Student> studentMap = new HashMap<>();
        if (!ids.isEmpty()) {
            List<Student> students = studentMapper.selectList(
                    new LambdaQueryWrapper<Student>().in(Student::getId, ids));
            for (Student s : students) {
                studentMap.put(s.getId(), s);
            }
        }

        for (Long sid : ids) {
            Student stu = studentMap.get(sid);
            // 学生不存在 / 不是本班的，都算越权：3002 而不是 1001，
            // 前端能据此区分"填错了"和"这人不归你管"
            if (stu == null || !classId.equals(stu.getClassId())) {
                throw new BizException(3002, "学生 " + sid + " 不在你的班里");
            }
            if (stu.getStatus() != null && stu.getStatus() == 0) {
                throw new BizException(1001, "「" + stu.getName() + "」已离班，不能排座");
            }
        }

        if (!dupStudent.isEmpty()) {
            Long sid = dupStudent.get(0);
            Student stu = studentMap.get(sid);
            String name = stu == null ? ("学生 " + sid) : ("「" + stu.getName() + "」");
            int[] p = dupPos.get(sid);
            throw new BizException(1001, name + " 被排到了两个座位（第 " + p[0] + " 排第 " + p[1] + " 列 是其中一个）");
        }

        // ---- 校验全过了，才动数据库 ----
        List<Seat> insertRows = new ArrayList<>(rows * cols);
        int seated = 0;
        for (int r = 1; r <= rows; r++) {
            for (int c = 1; c <= cols; c++) {
                Seat s = new Seat();
                s.setClassId(classId);
                s.setRowNo(r);
                s.setColNo(c);
                s.setStudentId(grid[r][c]);
                insertRows.add(s);
                if (grid[r][c] != null) {
                    seated++;
                }
            }
        }

        // 整图覆盖：先删后插，两步必须在同一个事务里。
        // 中间任何一步失败都会整体回滚，不会出现"座位删了但没插回去"
        seatMapper.delete(new LambdaQueryWrapper<Seat>().eq(Seat::getClassId, classId));
        if (!insertRows.isEmpty()) {
            seatMapper.batchInsert(insertRows);
        }

        return seated;
    }
}
