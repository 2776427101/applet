package com.banxing.classassistant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.banxing.classassistant.common.BizException;
import com.banxing.classassistant.dto.StudentInfoVO;
import com.banxing.classassistant.dto.StudentJoinDTO;
import com.banxing.classassistant.dto.StudentJoinVO;
import com.banxing.classassistant.dto.StudentLoginDTO;
import com.banxing.classassistant.dto.StudentLoginVO;
import com.banxing.classassistant.entity.ClassInfo;
import com.banxing.classassistant.entity.DirectMessage;
import com.banxing.classassistant.entity.Student;
import com.banxing.classassistant.entity.User;
import com.banxing.classassistant.mapper.ClassMapper;
import com.banxing.classassistant.mapper.StudentMapper;
import com.banxing.classassistant.mapper.UserMapper;
import com.banxing.classassistant.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 学生端身份（登录 + 我是谁）
 *
 * <p>叫 Portal 是为了和教师端的 {@link StudentService} 区分开：那个是"老师管学生"，
 * 这个是"学生自己进来"。两个混在一起，方法名迟早会撞。
 *
 * <p><b>学生不是 t_user 里的一行。</b> t_user 是"能登录的老师"，有 openid、角色、单位；
 * 学生是花名册里的一行，一辈子不该有密码。硬塞进 t_user 会带来一串麻烦：
 * 要处理"这个 user 其实不是老师"、登录时还要先建账号、换班时账号怎么算。
 *
 * <p>所以走另一条路：学号 + 姓名 换一张 role=2 的 token，
 * subject 放 studentId，claim 里带 classId。拦截器按路径分权（见 AuthInterceptor）。
 *
 * <p><b>v0.41：token 有两种。</b>花名册里查得到 → subject 是 studentId、带 classId，
 * 正常用；查不到 → subject 是 0、不带 classId，只能入班。这样"名单还没更新的人"
 * 也能登录，但登录本身拿不到任何班级数据 —— 数据权限仍然由 classId 决定。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StudentPortalService {

    private final ClassMapper classMapper;
    private final StudentMapper studentMapper;
    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final DirectMessageService directMessageService;

    @Value("${app.jwt.expire-seconds}")
    private long expireSeconds;

    /**
     * 学生登录：按学号找人。
     *
     * <p><b>v0.41：查不到也让他进来。</b>原来花名册里没有这个学号就直接报错，
     * 等于"只有被班主任导入过名单的人才能用学生端"。转专业的、插班的、
     * 名单还没更新的，全被挡在门外 —— 而他看到的只是一句"没找到学号"，
     * 不知道该找谁。现在改成发一张<b>没有班级的 token</b>，
     * 进去之后凭邀请码入班（见 {@link #join}）。
     *
     * <p>一个学号匹配到多个班时，返回班级列表让前端选，再带着 classId 请求一次。
     * 这一步不能省：直接把学生塞进第一个班，他会看到完全不相干的成绩和请假。
     */
    public StudentLoginVO login(StudentLoginDTO dto) {
        String no = dto.getStudentNo().trim();
        String name = dto.getName() == null ? "" : dto.getName().trim();

        List<Student> rows = studentMapper.selectList(new LambdaQueryWrapper<Student>()
                .eq(Student::getStudentNo, no)
                .eq(Student::getStatus, 1)
                .orderByAsc(Student::getClassId));

        /* 花名册里没有这个人 —— 不是错误，是"还没进班"。
           发一张只能用来入班的 token，前端会直接跳到入班页 */
        if (rows.isEmpty()) {
            return issueGuest(no, name);
        }

        /* 姓名必须对得上。v0.41 起姓名是必填项，这里就不再有"没填就跳过"的分支了：
           跳过等于给"输错学号登进别人账号"留了一个开关。

           比较前把空白全去掉：名单里是「黄旭铭」，学生在手机上打成「黄 旭铭」
           或者中间带个全角空格，都不该判成"不是同一个人" —— 名字里的空白
           从来不带信息，只带手误 */
        List<Student> sameName = rows.stream()
                .filter(s -> norm(name).equals(norm(s.getName())))
                .toList();
        if (sameName.isEmpty()) {
            throw new BizException(1004, "学号和姓名对不上。姓名要和班主任名单上的一致，差一个字也进不去");
        }
        rows = sameName;

        /* 同一个班可能因为导入重名有两行，按 classId 去重 ——
           否则"两个班"的选择器里会出现两个一模一样的班 */
        Map<Long, Student> byClass = new LinkedHashMap<>();
        for (Student s : rows) {
            byClass.putIfAbsent(s.getClassId(), s);
        }

        if (byClass.size() == 1) {
            return issue(byClass.values().iterator().next());
        }

        /* 多个班：指定了 classId 就直接进那个班 */
        if (dto.getClassId() != null) {
            Student hit = byClass.get(dto.getClassId());
            if (hit == null) {
                throw new BizException(1004, "学号 " + no + " 不在这个班里");
            }
            return issue(hit);
        }

        StudentLoginVO vo = new StudentLoginVO();
        vo.setNeedClass(true);
        List<StudentLoginVO.ClassOption> options = new ArrayList<>();
        for (Student s : byClass.values()) {
            ClassInfo c = classMapper.selectById(s.getClassId());
            StudentLoginVO.ClassOption o = new StudentLoginVO.ClassOption();
            o.setClassId(s.getClassId());
            o.setClassName(c == null ? "未命名班级" : c.getName());
            o.setTeacherName(teacherName(c));
            options.add(o);
        }
        vo.setClasses(options);
        return vo;
    }

    /**
     * 凭邀请码加入班级。
     *
     * <p>两种情况都走这里：
     * <ul>
     *   <li><b>已经在某个班</b>（token 里有 classId）—— 切换到另一个班，成绩/请假/座位/群聊全换过去；</li>
     *   <li><b>还没进任何班</b>（v0.41 的未入班 token，subject=0）—— 第一次入班。</li>
     * </ul>
     *
     * <p><b>花名册里没有这个学号时会新建一行。</b>这是 v0.41 和 v0.40 最大的差别：
     * 上一版拒绝自助入班，理由是怕学生往老师的名单里插一条没人知道的学籍。
     * 但那个理由的代价是"名单没更新的人永远进不来" —— 而现在登录本身已经放开了，
     * 再卡住入班，等于让人登录完看到一堵墙。
     *
     * <p>三条约束把风险压住：
     * <ol>
     *   <li>必须持有邀请码（老师发的，等于老师授权）；</li>
     *   <li>该学号在本班已有在读行时，<b>姓名必须一致</b>才绑定 —— 挡住"拿别人学号入班"；</li>
     *   <li>该学号在本班是<b>离班</b>状态时拒绝，提示找班主任恢复，不新建重复行。</li>
     * </ol>
     */
    public StudentJoinVO join(Long classId, Long studentId,
                              String tokenStudentNo, String tokenName,
                              StudentJoinDTO dto) {
        String no;
        String name;

        if (studentId != null) {
            /* 已经在某个班里：学号姓名以库里那行为准，不信 token */
            Student me = requireRow(classId, studentId);
            no = me.getStudentNo();
            name = me.getName();
        } else {
            /* 未入班：库里没有这个人，只能信 token 里的学号和姓名。
               这两个值是登录时手填的，也是新建花名册行唯一的来源 */
            no = tokenStudentNo == null ? "" : tokenStudentNo.trim();
            name = tokenName == null ? "" : tokenName.trim();
            if (no.isEmpty() || name.isEmpty()) {
                throw new BizException(1002, "登录信息不完整，请重新登录");
            }
        }

        if (no.isEmpty()) {
            throw new BizException(1004, "你的名单里没有学号，找班主任补一下");
        }

        ClassInfo target = findByInviteCode(dto.getInviteCode().trim());
        if (target == null) {
            throw new BizException(1004, "邀请码不对，找班主任要一下");
        }

        /* 邀请码对但班已归档：同样说"邀请码不对"。
           告诉学生"这个班已归档"既帮不上忙，也泄露了别的班的状态 */
        if (target.getStatus() == null || target.getStatus() != 1) {
            throw new BizException(1004, "邀请码不对，找班主任要一下");
        }

        boolean alreadyIn = target.getId().equals(classId);

        /* 查这个学号在目标班的全部行（**不带 status 条件**）。
           要区分"从没来过"和"来过又走了"：前者可以新建，后者不能 ——
           给离班的人再建一行，老师的名单里就会出现两个同号的人 */
        List<Student> rows = studentMapper.selectList(new LambdaQueryWrapper<Student>()
                .eq(Student::getClassId, target.getId())
                .eq(Student::getStudentNo, no)
                .orderByAsc(Student::getId));

        Student row = null;
        for (Student r : rows) {
            if (r.getStatus() != null && r.getStatus() == 1) {
                row = r;
                break;
            }
        }

        if (row == null) {
            if (!rows.isEmpty()) {
                throw new BizException(1004,
                        "你之前在这个班，现在不在名单里了，找班主任把你恢复");
            }
            row = createRow(target, no, name);
        } else if (!norm(name).equals(norm(row.getName()))) {
            /* 学号在班里叫另一个名字：要么输错了学号，要么想拿别人的学号进来。
               两种都该拦，而且要把班里的名字说出来 —— 说"对不上"学生只会反复重试 */
            throw new BizException(1004,
                    "「" + target.getName() + "」里的学号 " + no + " 叫「" + row.getName()
                            + "」，和你填的姓名对不上");
        }

        StudentJoinVO vo = new StudentJoinVO();
        vo.setToken(jwtUtil.createStudentToken(row.getId(), row.getClassId(), row.getStudentNo(), row.getName()));
        vo.setExpiresIn(expireSeconds);
        vo.setStudent(info(row, target));
        vo.setAlreadyIn(alreadyIn);
        log.info("学生加入班级：studentId={} {} -> classId={} alreadyIn={}",
                row.getId(), row.getStudentNo(), target.getId(), alreadyIn);
        return vo;
    }

    /**
     * 给学生自助入班时新建一行花名册。
     *
     * <p>status 直接给 1（在读）而不是"待审核"：老师端的名单页只认 1（在读）和 0（离班），
     * 塞一个 2 进去，学生入了班但班主任在任何列表里都看不到他 —— 比不建还糟。
     * 老师发邀请码这个动作本身就是授权，建行只是把他的授权落成数据。
     */
    private Student createRow(ClassInfo clazz, String studentNo, String name) {
        Student s = new Student();
        s.setClassId(clazz.getId());
        s.setName(name);
        s.setStudentNo(studentNo);
        s.setGender(0);
        s.setStatus(1);
        studentMapper.insert(s);
        log.info("学生自助入班，新建花名册行：classId={} studentNo={} name={} studentId={}",
                clazz.getId(), studentNo, name, s.getId());
        return s;
    }

    /**
     * 按邀请码找班。
     *
     * <p>大小写不敏感：邀请码在生成时是大写字母+数字，但学生在手机上抄的时候
     * 经常全小写。先按原样查一次，查不到再转大写查 —— 只做这两次，
     * 不做模糊匹配（模糊匹配会让"差一个字母"的码也命中别的班）。
     */
    private ClassInfo findByInviteCode(String code) {
        if (!StringUtils.hasText(code)) {
            return null;
        }
        ClassInfo hit = classMapper.selectOne(new LambdaQueryWrapper<ClassInfo>()
                .eq(ClassInfo::getInviteCode, code)
                .last("LIMIT 1"));
        if (hit != null) {
            return hit;
        }
        return classMapper.selectOne(new LambdaQueryWrapper<ClassInfo>()
                .eq(ClassInfo::getInviteCode, code.toUpperCase())
                .last("LIMIT 1"));
    }

    /** 取当前登录的学生（存在 / 在读 / 属于 token 里那个班），不满足就 1002 */
    private Student requireRow(Long classId, Long studentId) {
        if (classId == null || studentId == null) {
            throw new BizException(1002, "登录已失效，请重新登录");
        }
        Student s = studentMapper.selectById(studentId);
        if (s == null || s.getStatus() == null || s.getStatus() != 1) {
            throw new BizException(1002, "登录已失效，请重新登录");
        }
        if (!classId.equals(s.getClassId())) {
            throw new BizException(1002, "登录信息已过期，请重新登录");
        }
        return s;
    }

    /**
     * 姓名比较用的归一化：去掉所有空白（含全角空格）。
     *
     * <p>只用于"是不是同一个人"的判断，不用于入库 —— 存进去的仍是学生填的原样。
     */
    private static String norm(String s) {
        return s == null ? "" : s.replaceAll("[\\s\\u3000]", "");
    }

    /** 签发 token + 带上"我是谁" */
    private StudentLoginVO issue(Student s) {
        ClassInfo clazz = classMapper.selectById(s.getClassId());
        StudentLoginVO vo = new StudentLoginVO();
        vo.setToken(jwtUtil.createStudentToken(s.getId(), s.getClassId(), s.getStudentNo(), s.getName()));
        vo.setExpiresIn(expireSeconds);
        vo.setStudent(info(s, clazz));
        log.info("学生登录：studentId={} classId={} no={}", s.getId(), s.getClassId(), s.getStudentNo());
        return vo;
    }

    /**
     * 未入班：发一张没有班级的 token。
     *
     * <p>返回的 student 里只有姓名和学号，classId / className 全是 null ——
     * 前端据此把首页换成"还没加入班级"，而不是显示一个空班级的零分成绩。
     */
    private StudentLoginVO issueGuest(String studentNo, String name) {
        StudentLoginVO vo = new StudentLoginVO();
        vo.setToken(jwtUtil.createGuestStudentToken(studentNo, name));
        vo.setExpiresIn(expireSeconds);

        StudentInfoVO info = new StudentInfoVO();
        info.setName(name);
        info.setStudentNo(studentNo);
        info.setUnread(0);
        vo.setStudent(info);
        vo.setNeedJoin(true);

        log.info("学生登录（未入班）：no={} name={}", studentNo, name);
        return vo;
    }

    /**
     * 学生端"我是谁"
     *
     * <p>未入班时 classId 为 null，只回姓名和学号 —— 这个方法会被入班页调用，
     * 用来显示"你好，XXX"。它也是未入班 token 唯一能调的读取接口。
     */
    public StudentInfoVO me(Long classId, Long studentId, String studentNo, String name) {
        if (classId == null) {
            StudentInfoVO vo = new StudentInfoVO();
            vo.setName(name);
            vo.setStudentNo(studentNo);
            vo.setClassName("");
            vo.setTeacherName("");
            vo.setUnread(0);
            return vo;
        }

        Student s = studentMapper.selectById(studentId);
        if (s == null || s.getStatus() == null || s.getStatus() != 1) {
            throw new BizException(1002, "登录已失效，请重新登录");
        }
        if (!s.getClassId().equals(classId)) {
            throw new BizException(1002, "登录信息已过期，请重新登录");
        }
        return info(s, classMapper.selectById(classId));
    }

    /* ================================================================
       杂项
       ================================================================ */

    /* 原来这里有个 findByInviteCode()：按邀请码找班。
       v0.38 登录改成按学号找人之后它就没有调用方了，直接删掉 ——
       留着的话，下一个人会以为"登录还需要邀请码"，又把它接回去。 */

    private StudentInfoVO info(Student s, ClassInfo clazz) {
        StudentInfoVO vo = new StudentInfoVO();
        vo.setId(s.getId());
        vo.setName(s.getName());
        vo.setStudentNo(s.getStudentNo());
        vo.setGender(s.getGender());
        vo.setClassId(s.getClassId());
        vo.setClassName(clazz == null ? "" : clazz.getName());
        vo.setTeacherName(teacherName(clazz));
        vo.setUnread(directMessageService.unreadCount(
                s.getClassId(), s.getId(), DirectMessage.FROM_TEACHER));
        return vo;
    }

    private String teacherName(ClassInfo clazz) {
        if (clazz == null || clazz.getHeadTeacherId() == null) {
            return "班主任";
        }
        User t = userMapper.selectById(clazz.getHeadTeacherId());
        return t == null || !StringUtils.hasText(t.getName()) ? "班主任" : t.getName();
    }
}
