package com.banxing.classassistant.dto;

import lombok.Data;

import java.util.List;

/**
 * 保存座位图的入参：整图覆盖。
 *
 * <p>不提供"移动一个座位"的接口，因为拖拽场景下前端手里本来就有整张图。
 * 做单点移动会引入"两个人交换座位"的中间态，还得为这个中间态写一个
 * 临时状态或者两次请求，反而更容易出错。
 *
 * <p>seats 里可以只传有人的格子，后端会按 rows × cols 补满空格子再落库。
 * 这样即使前端偷懒只传 47 条，也不会把行列数弄丢。
 */
@Data
public class SeatSaveDTO {

    // 1~20，超出直接 1001
    private Integer rows;

    // 1~20
    private Integer cols;

    private List<Item> seats;

    @Data
    public static class Item {

        private Integer row;

        private Integer col;

        // 可以是 null，表示这个位置空着
        private Long studentId;
    }
}
