package com.banxing.classassistant.service;

import com.banxing.classassistant.dto.TodoCreateDTO;
import com.banxing.classassistant.dto.TodoListVO;

/**
 * 待办业务接口
 * 每个方法第一个参数都是 userId，从 token 里来，不接受前端传
 */
public interface TodoService {

    /** 列表。done 不传=全部，0=未完成，1=已完成 */
    TodoListVO list(Long userId, Integer done);

    /** 新增，返回新记录的 id */
    Long add(Long userId, TodoCreateDTO dto);

    /** 完成 / 取消完成 */
    void setDone(Long userId, Long id, Integer done);

    /** 删除（物理删除） */
    void delete(Long userId, Long id);
}