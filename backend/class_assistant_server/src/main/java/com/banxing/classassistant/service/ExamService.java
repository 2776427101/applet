package com.banxing.classassistant.service;

import com.banxing.classassistant.dto.ExamCreateDTO;
import com.banxing.classassistant.dto.ExamListVO;

/**
 * 考试业务
 */
public interface ExamService {

    /** 某个班的考试列表，按考试日期倒序。classId 为 null = 我的第一个班 */
    ExamListVO list(Long userId, Long classId, Integer page, Integer size);

    /** 新建考试，返回新考试 id。classId 决定这次考试落在哪个班 */
    Long create(Long userId, Long classId, ExamCreateDTO dto);
}
