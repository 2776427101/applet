package com.banxing.classassistant.dto;
import com.banxing.classassistant.entity.Student;
import lombok.Data;
import java.time.LocalDate;
/**
 * 学生信息（返回给前端）
 * 字段严格按 api-contract.md §6.1，一个不多一个不少
 */
@Data
public class StudentVO {
    private Long id;

    private String name;

    // 0=未知 1=男 2=女
    private Integer gender;

    // 生日，可能为 null
    private LocalDate birthday;

    // 学号，可能为 null
    private String studentNo;

    // 1=在读 0=已离班
    private Integer status;

    /**
     * 实体转 VO。
     * 放在 VO 里当静态工厂，比在 Service 里写一堆 set 干净，
     * 以后别的接口（详情、搜索）也能复用
     */
    public static StudentVO from(Student s) {
        StudentVO vo = new StudentVO();
        vo.setId(s.getId());
        vo.setName(s.getName());
        vo.setGender(s.getGender());
        vo.setBirthday(s.getBirthday());
        vo.setStudentNo(s.getStudentNo());
        vo.setStatus(s.getStatus());
        return vo;
    }
}
