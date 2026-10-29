package com.banxing.classassistant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.banxing.classassistant.common.BizException;
import com.banxing.classassistant.dto.ClassSaveDTO;
import com.banxing.classassistant.dto.ClassVO;
import com.banxing.classassistant.entity.ClassInfo;
import com.banxing.classassistant.entity.Student;
import com.banxing.classassistant.entity.User;
import com.banxing.classassistant.mapper.ClassMapper;
import com.banxing.classassistant.mapper.StudentMapper;
import com.banxing.classassistant.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 教师端公共上下文：当前登录的是不是老师、他带的哪个班。
 *
 * <p>每个教师端接口开头都要做两件事：
 * <ol>
 *   <li>校验角色 —— 拦截器只验了 token 有没有效，没验角色</li>
 *   <li>查出他带的班 —— 班级 id 只能从 token 里的 userId 推，绝不能用前端传的 classId</li>
 * </ol>
 *
 * <p>这两件事原来在 Home / Student / Todo / Leave 四个 ServiceImpl 里各抄了一遍，
 * 一共 4 份。改一处漏三处只是时间问题，所以收到这里。
 *
 * <p>没写成 interface + impl：它不存在"多种实现"的可能，
 * 多一层接口只会让人多点一次「跳转到实现」。
 */
@Service
@RequiredArgsConstructor
public class ClassService {

    private final UserMapper userMapper;
    private final ClassMapper classMapper;
    private final StudentMapper studentMapper;

    /**
     * 校验当前用户是老师，不是就抛 3001。
     * 返回 User 本身，因为首页要拿它的 name 显示「班主任：张老师」。
     */
    public User requireTeacher(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || user.getRole() == null || user.getRole() != 1) {
            throw new BizException(3001, "当前用户不是老师，无权访问");
        }
        return user;
    }

    /**
     * 当前老师带的班；没绑班返回 null。
     * 注意 null 不是错误，是「还没建班」这个正常状态，调用方要自己决定怎么兜。
     */
    public ClassInfo myClass(Long userId) {
        return classMapper.selectOne(
                new LambdaQueryWrapper<ClassInfo>().eq(ClassInfo::getHeadTeacherId, userId));
    }

    /**
     * 当前老师带的班 id；没绑班返回 null。
     * 只需要 id 时用这个，少写一层 clazz == null ? null : clazz.getId()。
     */
    public Long myClassId(Long userId) {
        ClassInfo clazz = myClass(userId);
        return clazz == null ? null : clazz.getId();
    }

    /**
     * 班级设置页要的数据。
     *
     * <p>比 {@link #myClass} 多查一次在读人数 —— 设置页显示「本班 47 人」，
     * 是为了让老师在改名之前确认「我改的是哪个班」。
     * 改班级名是个低频但影响面很大的操作（首页、导出、名单都跟着变），
     * 多给一个「现在班里有几个人」能有效防手滑。
     */
    public ClassVO detail(Long userId) {
        requireTeacher(userId);
        ClassInfo clazz = requireMyClass(userId);

        ClassVO vo = new ClassVO();
        vo.setId(clazz.getId());
        vo.setName(clazz.getName());
        vo.setGrade(clazz.getGrade());
        vo.setTermStartDate(clazz.getTermStartDate());
        vo.setInviteCode(clazz.getInviteCode());
        vo.setStudentCount(studentMapper.selectCount(new LambdaQueryWrapper<Student>()
                .eq(Student::getClassId, clazz.getId())
                .eq(Student::getStatus, 1)).intValue());
        return vo;
    }

    /**
     * 修改班级名 / 年级 / 开学日期。
     *
     * <p>只更新这三个字段。邀请码和 status 不在 DTO 里，
     * 前端就算传了也会被 Jackson 忽略 —— DTO 的字段列表就是权限边界。
     *
     * <p><b>「传什么改什么」而不是「全量覆盖」</b>：传 null 的字段不进 SET 子句。
     * 名单导入页要一键把班名改成名单里那个，它只想改名字，
     * 如果接口要求必须连年级和开学日期一起传，前端就得先 GET 一次再 PUT ——
     * 多一次往返，而且 GET 回来的值一旦是 null，PUT 上去就把数据清空了。
     *
     * <p>不加 {@code @Transactional}：单表单行 UPDATE，本身就是一个原子操作。
     */
    public void update(Long userId, ClassSaveDTO dto) {
        requireTeacher(userId);
        ClassInfo clazz = requireMyClass(userId);

        LambdaUpdateWrapper<ClassInfo> w = new LambdaUpdateWrapper<ClassInfo>()
                .eq(ClassInfo::getId, clazz.getId())
                // 再带一次 head_teacher_id：和 StudentServiceImpl.update 一样，
                // 上面虽然校验过，但写进 WHERE 是零成本的兜底
                .eq(ClassInfo::getHeadTeacherId, userId)
                .set(ClassInfo::getName, dto.getName().trim());

        // 空串是「把年级清空」，是有意义的操作，所以只判 null 不判空
        if (dto.getGrade() != null) {
            w.set(ClassInfo::getGrade, dto.getGrade().trim());
        }
        // term_start_date 是 NOT NULL，没有「清空」这个语义，null 一律当作没传
        if (dto.getTermStartDate() != null) {
            w.set(ClassInfo::getTermStartDate, dto.getTermStartDate());
        }

        classMapper.update(null, w);
    }

    /**
     * 当前老师带的班，没绑班就抛 1001。
     *
     * <p>和 {@link #myClass} 的区别：那个返回 null 表示「还没建班」这个正常状态，
     * 调用方自己兜。但「改班级设置」没有班就没法做，是硬错误。
     */
    private ClassInfo requireMyClass(Long userId) {
        ClassInfo clazz = myClass(userId);
        if (clazz == null) {
            throw new BizException(1001, "你还没有绑定班级");
        }
        return clazz;
    }
}
