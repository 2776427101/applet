package com.banxing.classassistant.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 成员列表响应。容器人数和班级人数同量级（几十到几百），不分页。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MemberListVO {

    private int total;

    /** 在群人数（status=1），和 total 可能不同：筛选「已移除」时 total 是筛选后的行数 */
    private int activeCount;

    private List<MemberVO> list;
}
