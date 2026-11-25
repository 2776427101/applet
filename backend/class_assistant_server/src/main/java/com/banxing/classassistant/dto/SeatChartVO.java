package com.banxing.classassistant.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 座位图返回结构。
 *
 * <p>一次请求把整页要的东西全给齐：格子、行列数、还没排座的人。
 * 如果让前端再单独调一次学生列表自己算差集，页面就会出现
 * "座位画好了、未排座人数还是 0"的半截状态。
 */
@Data
@AllArgsConstructor
public class SeatChartVO {

    // 排数（纵向）
    private int rows;

    // 列数（横向）
    private int cols;

    // 全部格子，共 rows × cols 个，空座位也在里面
    private List<SeatVO> seats;

    // 本班在读但还没排座的学生
    private List<SeatStudentVO> unseated;
}
