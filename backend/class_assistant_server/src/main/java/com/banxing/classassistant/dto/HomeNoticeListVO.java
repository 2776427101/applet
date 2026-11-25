package com.banxing.classassistant.dto;

import lombok.Data;

import java.util.List;

/**
 * 首页通知列表（出参）
 */
@Data
public class HomeNoticeListVO {

    /** 通知总条数。首页框右上角显示「共 N 条」，N 用这个，不是 list.size() */
    private long total;

    /** 最多 limit 条，首页只需要第一条，通知页要全部 */
    private List<HomeNoticeVO> list;
}
