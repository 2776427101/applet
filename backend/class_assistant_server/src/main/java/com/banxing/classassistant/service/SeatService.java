package com.banxing.classassistant.service;

import com.banxing.classassistant.dto.SeatChartVO;
import com.banxing.classassistant.dto.SeatSaveDTO;

/**
 * 座位业务
 */
public interface SeatService {

    /** 读某个班的整张座位图（含空位和未排座学生）。classId 为 null = 我的第一个班 */
    SeatChartVO chart(Long userId, Long classId);

    /** 整图覆盖保存到指定班级，返回已排座的学生人数 */
    int save(Long userId, Long classId, SeatSaveDTO dto);
}
