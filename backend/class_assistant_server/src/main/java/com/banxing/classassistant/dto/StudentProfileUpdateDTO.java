package com.banxing.classassistant.dto;

import lombok.Data;

/**
 * 学生修改自己的资料（入参）
 *
 * <p><b>只放三个字段，而且是白名单。</b>学生能改的是"联系方式"这类
 * 他自己比老师更清楚、又不会影响班级管理的东西：
 * <ul>
 *   <li>phone / wechat：换了号码自己就能更新，不用等老师</li>
 *   <li>dormitory：换宿舍同理</li>
 * </ul>
 *
 * <p>姓名、学号、班级、生日一律不给改 —— 这些是学籍信息，
 * 学生改了就和对不上名册，而且"改名字"这种操作没有审批流程兜着。
 * 想改只能找班主任，走老师端的学生编辑。
 *
 * <p>null 表示"这一项不改"，空串表示"清空"。两者必须能区分，
 * 否则前端只改手机号时，会把宿舍一起清掉。
 */
@Data
public class StudentProfileUpdateDTO {

    private String phone;

    private String wechat;

    private String dormitory;
}
