package com.banxing.classassistant.service;

import com.banxing.classassistant.dto.ScoreBatchDTO;
import com.banxing.classassistant.dto.ScoreListVO;
import com.banxing.classassistant.dto.ScoreStatsVO;

/**
 * 成绩业务
 */
public interface ScoreService {

    /** 某次考试的成绩表（一个学生一行，含总分和班内排名） */
    ScoreListVO list(Long userId, Long examId);

    /** 批量录入，返回实际写入 / 更新的成绩条数 */
    int batchSave(Long userId, ScoreBatchDTO dto);

    /** 某次考试的统计（单科 + 总分 + 年级排名） */
    ScoreStatsVO stats(Long userId, Long examId);
}
