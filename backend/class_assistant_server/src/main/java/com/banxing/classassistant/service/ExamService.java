package com.banxing.classassistant.service;

import com.banxing.classassistant.dto.ExamCreateDTO;
import com.banxing.classassistant.dto.ExamListVO;

/**
 * 考试业务
 */
public interface ExamService {

    /** 考试列表，按考试日期倒序 */
    ExamListVO list(Long userId, Integer page, Integer size);

    /** 新建考试，返回新考试 id */
    Long create(Long userId, ExamCreateDTO dto);
}
