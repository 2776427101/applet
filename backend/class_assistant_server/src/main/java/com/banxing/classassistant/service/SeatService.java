package com.banxing.classassistant.service;

import com.banxing.classassistant.dto.SeatChartVO;
import com.banxing.classassistant.dto.SeatSaveDTO;

/**
 * 座位业务
 */
public interface SeatService {

    /** 读整张座位图（含空位和未排座学生） */
    SeatChartVO chart(Long userId);

    /** 整图覆盖保存，返回已排座的学生人数 */
    int save(Long userId, SeatSaveDTO dto);
}
