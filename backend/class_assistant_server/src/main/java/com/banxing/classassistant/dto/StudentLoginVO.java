package com.banxing.classassistant.dto;

import lombok.Data;

import java.util.List;

/**
 * 学生登录结果
 *
 * <p>四种形态：
 * <ol>
 *   <li>正常登录 → {@code token} 有值，{@code needClass=false}</li>
 *   <li>学号在多个班 → {@code token=null}、{@code needClass=true}、{@code classes} 有值</li>
 *   <li><b>还没进任何班</b> → {@code token} 有值、{@code needJoin=true}、{@code student.classId=null}</li>
 *   <li>失败 → 走 BizException，不会是 200</li>
 * </ol>
 *
 * <p><b>为什么"选班"不拆成两个接口</b>：拆开的话前端要先调一次"查我有哪些班"，
 * 再调一次"登录"，中间那次请求的入参（学号）和第一次完全一样。
 * 合成一个，前端只多一个分支，少一次往返。
 */
@Data
public class StudentLoginVO {

    /** 登录成功才有；需要选班时是 null */
    private String token;

    private long expiresIn;

    /** 登录成功才有。needJoin=true 时只有 name / studentNo，班级字段全是 null */
    private StudentInfoVO student;

    /** true = 学号匹配到多个班，前端要弹一个班级选择器 */
    private boolean needClass;

    /** needClass=true 时非空，只含班级名和班主任，不含任何成绩/名单 */
    private List<ClassOption> classes;

    /**
     * true = 花名册里没有这个人，token 是「未入班」的。
     * 前端拿它直接跳入班页，不要往首页走 —— 首页那几个接口会 1009。
     */
    private boolean needJoin;

    @Data
    public static class ClassOption {
        private Long classId;
        private String className;
        private String teacherName;
    }
}
