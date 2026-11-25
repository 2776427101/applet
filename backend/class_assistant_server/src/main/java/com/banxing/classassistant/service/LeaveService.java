package com.banxing.classassistant.service;

import com.banxing.classassistant.dto.LeaveAuditDTO;
import com.banxing.classassistant.dto.LeaveCreateDTO;
import com.banxing.classassistant.dto.LeaveListVO;

/**
 * 请假业务接口
 */
public interface LeaveService {

    /**
     * 请假列表
     *
     * @param classId 看哪个班；null = 我的第一个班
     * @param status  -1=全部，0 待审批，1 已通过，2 已驳回，3 已销假
     */
    LeaveListVO list(Long userId, Long classId, Integer status, Integer page, Integer size);

    /** 审批：只能 0 → 1 或 0 → 2。归属按记录自己的 class_id 校验 */
    void audit(Long userId, Long id, LeaveAuditDTO dto);

    /** 代学生请假，默认已通过，返回新记录 id。classId 决定落到哪个班 */
    Long add(Long userId, Long classId, LeaveCreateDTO dto);
}
