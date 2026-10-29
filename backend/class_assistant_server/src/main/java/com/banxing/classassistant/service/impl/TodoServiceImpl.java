package com.banxing.classassistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.banxing.classassistant.common.BizException;
import com.banxing.classassistant.dto.TodoCreateDTO;
import com.banxing.classassistant.dto.TodoListVO;
import com.banxing.classassistant.dto.TodoVO;
import com.banxing.classassistant.entity.ClassInfo;
import com.banxing.classassistant.entity.Todo;
import com.banxing.classassistant.mapper.TodoMapper;
import com.banxing.classassistant.service.ClassService;
import com.banxing.classassistant.service.TodoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 待办业务实现
 */
@Service
@RequiredArgsConstructor
public class TodoServiceImpl implements TodoService {

    private final ClassService classService;
    private final TodoMapper todoMapper;

    // ==================== 列表 ====================

    @Override
    public TodoListVO list(Long userId, Integer done) {
        classService.requireTeacher(userId);

        ClassInfo clazz = classService.myClass(userId);
        if (clazz == null) {
            // 没绑班 = 还没有待办可看，不是错误
            return new TodoListVO(0, Collections.emptyList());
        }

        LambdaQueryWrapper<Todo> qw = new LambdaQueryWrapper<>();

        // 条件 1：只查自己的。少了这行，能看到别的老师的待办
        qw.eq(Todo::getTeacherId, userId);

        // 条件 2：只查自己班的。少了这行，换班之后会看到上个班的旧待办
        qw.eq(Todo::getClassId, clazz.getId());

        // 条件 3：done 传了才过滤。
        // 注意这里用 != null 判断，不能用 if (done != null && done > 0)，
        // 因为 0（未完成）是有效值，那样写会让"只看未完成"变成"看全部"
        if (done != null) {
            qw.eq(Todo::getDone, done);
        }

        // 排序：未完成在前；有截止时间的按早的在前；没有截止时间的排最后
        //
        // 为什么用 last() 而不是 orderByAsc()：
        // "due_at IS NULL" 这种表达式 MyBatis-Plus 的链式方法表达不了，
        // 只能手写。last() 会把这段原样拼到 SQL 最后。
        //
        // ⚠️ last() 里只能写常量字符串，绝对不能拼前端传来的值，
        //    否则就是 SQL 注入。这里全是写死的列名，安全。
        //
        // 另外：不能写成 orderByAsc(Todo::getDone).last("ORDER BY ...")，
        // 那样会生成两个 ORDER BY，SQL 直接报错
        qw.last("ORDER BY done ASC, due_at IS NULL, due_at ASC, created_at DESC");

        List<Todo> rows = todoMapper.selectList(qw);
        List<TodoVO> list = rows.stream().map(TodoVO::from).toList();

        return new TodoListVO(list.size(), list);
    }

    // ==================== 新增 ====================

    @Override
    public Long add(Long userId, TodoCreateDTO dto) {
        classService.requireTeacher(userId);

        ClassInfo clazz = classService.myClass(userId);
        if (clazz == null) {
            // 待办表里 class_id 是 NOT NULL，没班就存不进去，必须提前拦住
            throw new BizException(1001, "请先绑定班级");
        }

        // 手动校验：trim 之后再判空，防止前端传一堆空格
        String title = dto.getTitle() == null ? "" : dto.getTitle().trim();
        if (title.isEmpty()) {
            throw new BizException(1001, "待办内容不能为空");
        }
        if (title.length() > 128) {
            // 数据库 title 是 VARCHAR(128)，超了会抛 SQL 异常变成 5000，
            // 提前拦住，给个能看懂的提示
            throw new BizException(1001, "待办内容不能超过 128 字");
        }

        Todo todo = new Todo();
        // 这三个字段只能由后端决定，前端说了不算
        todo.setTeacherId(userId);
        todo.setClassId(clazz.getId());
        todo.setDone(0);

        todo.setTitle(title);
        todo.setDueAt(dto.getDueAt());

        // created_at / updated_at 不用设。
        // 数据库建表时写了 DEFAULT CURRENT_TIMESTAMP，
        // 而 MyBatis-Plus 插入时会跳过 null 字段（默认策略 NOT_NULL），
        // 所以这两个字段不在 INSERT 语句里，数据库自己填。
        todoMapper.insert(todo);

        // insert 之后主键会被回填到 todo.id（@TableId(type = IdType.AUTO) 的作用）
        return todo.getId();
    }

    // ==================== 完成 / 取消完成 ====================

    @Override
    public void setDone(Long userId, Long id, Integer done) {
        classService.requireTeacher(userId);

        if (done == null || (done != 0 && done != 1)) {
            throw new BizException(1001, "done 只能是 0 或 1");
        }

        Todo exist = todoMapper.selectById(id);
        if (exist == null) {
            throw new BizException(1004, "待办不存在");
        }
        // 归属校验：不是自己的，返回 3002，不是 1004
        if (!exist.getTeacherId().equals(userId)) {
            throw new BizException(3002, "这条待办不属于你");
        }

        // 只 new 一个带 id 和 done 的对象去更新，
        // MyBatis-Plus 会跳过 null 字段，生成的 SQL 是：
        //   UPDATE t_todo SET done=? WHERE id=?
        // 如果拿上面查出来的 exist 去改，会把 title、due_at 等字段一起写一遍，
        // 万一中间有人改了标题，就会被这次更新覆盖掉
        Todo update = new Todo();
        update.setId(id);
        update.setDone(done);
        todoMapper.updateById(update);
    }

    // ==================== 删除 ====================

    @Override
    public void delete(Long userId, Long id) {
        classService.requireTeacher(userId);

        Todo exist = todoMapper.selectById(id);
        if (exist == null) {
            throw new BizException(1004, "待办不存在");
        }
        if (!exist.getTeacherId().equals(userId)) {
            throw new BizException(3002, "这条待办不属于你");
        }

        // 待办是物理删除。学生是软删除（status=0），因为请假、成绩还指向他；
        // 待办是一次性任务，没有别的表引用，删了就删了
        todoMapper.deleteById(id);
    }
}