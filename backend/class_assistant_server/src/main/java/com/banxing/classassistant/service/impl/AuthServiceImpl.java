package com.banxing.classassistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.banxing.classassistant.common.BizException;
import com.banxing.classassistant.dto.LoginVO;
import com.banxing.classassistant.dto.ProfileUpdateDTO;
import com.banxing.classassistant.entity.ClassInfo;
import com.banxing.classassistant.entity.User;
import com.banxing.classassistant.mapper.ClassMapper;
import com.banxing.classassistant.mapper.UserMapper;
import com.banxing.classassistant.service.AuthService;
import com.banxing.classassistant.util.JwtUtil;
import com.banxing.classassistant.util.WxApiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 登录业务实现
 */
//Spring 注解，把这个类注册成一个 Bean 交给 Spring 容器管理，其他类（如 Controller）就能通过@Autowired直接注入使用，不用自己new
@Service
//编译时自动生成一个构造函数，参数是所有 final 字段。
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final ClassMapper classMapper;
    private final WxApiClient wxApiClient; // 微信接口工具类
    private final JwtUtil jwtUtil; // JWT 工具类

    // token 有效期，从 yml 读，保证签发时和返回给前端的值一致
    @Value("${app.jwt.expire-seconds}")
    private long expireSeconds;

    @Override
    public LoginVO login(String code) {
        // 第一步：code 换 openid（具体走真实接口还是 mock，由 WxApiClient 内部决定）
        String openid = wxApiClient.code2Openid(code);

        // 第二步：按 openid 查用户，查不到说明是新用户
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getOpenid, openid));

        if (user == null) {
            // 新用户：设定数据插入数据库，默认角色为1（老师）当前自用场景
            // 真实项目应该走"邀请码/后台开通"，不能让人自助变成老师
            user = new User();
            user.setOpenid(openid);
            user.setRole(1);
            user.setName("");
            user.setStatus(1);
            userMapper.insert(user);
        }
        // 第三步：更新最后登录时间（updateById 只更新主键对应的那条）
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);
        // 第四步：查他带的班（多班级场景下只取第一个当默认班，理由见 firstClassOf）
        ClassInfo clazz = firstClassOf(user.getId());
        // 第五步：组装返回
        LoginVO vo = new LoginVO();
        vo.setToken(jwtUtil.createToken(user.getId(), user.getRole()));
        vo.setExpiresIn(expireSeconds);
        // 没查到班级，说明还没绑定，前端要引导他去加入班级
        vo.setNeedBindClass(clazz == null);
        vo.setUser(toUserVO(user, clazz));
        return vo;
    }

    @Override
    public LoginVO.UserVO currentUser(Long userId) {
        // 按 ID 查用户，查不到说明 token 里的 ID 已经失效（用户被删了）
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(1002, "用户不存在，请重新登录");
        }
        // 同一个人可能没带班，所以这里允许 clazz 为 null
        ClassInfo clazz = firstClassOf(user.getId());
        return toUserVO(user, clazz);
    }

    /**
     * 取该老师名下的「默认班级」，没有则返回 null。
     *
     * 为什么不能用 selectOne：一个老师现在可以有多个班，而 selectOne 查到 2 条会抛
     * TooManyResultsException，被 GlobalExceptionHandler 的 Exception 兜底成
     * 5000「服务器开小差了」—— 症状是「登录不上」，但日志里看不出和班级有关，
     * 极难定位。多班级改造后这里必须改成「查列表再取第一条」。
     *
     * 两个细节：
     * 1) 按 id 升序 —— 取最早建的那个班，保证每次登录拿到的默认班一致，
     *    否则每次登录默认班都可能变，老师会以为数据串了。
     * 2) 必须过滤 status=1 —— 删班是归档，归档班不能再当默认班。
     */
    private ClassInfo firstClassOf(Long userId) {
        List<ClassInfo> list = classMapper.selectList(new LambdaQueryWrapper<ClassInfo>()
                .eq(ClassInfo::getHeadTeacherId, userId)
                .eq(ClassInfo::getStatus, 1)
                .orderByAsc(ClassInfo::getId));
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public LoginVO.UserVO updateProfile(Long userId, ProfileUpdateDTO dto) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(1002, "用户不存在，请重新登录");
        }

        /* 只 set 表单里有的三个字段。
           不要拿 dto 整个覆盖到 user 上 —— DTO 里没有 role / status，
           覆盖会把它们写成 null，等于把这个人降权 + 禁用 */
        user.setName(dto.getName().trim());
        /* phone / unit 传 null 表示「不改」，传空串表示「清空」。
           MyBatis-Plus 的 updateById 默认忽略 null 字段，
           所以 null 天然就是「不改」，正好符合语义 */
        user.setPhone(dto.getPhone() == null ? null : dto.getPhone().trim());
        user.setUnit(dto.getUnit() == null ? null : dto.getUnit().trim());

        userMapper.updateById(user);

        /* 改完直接返回最新的用户信息：前端保存后要用它刷新页面，
           让它再发一次 GET 只是多一次往返和一个失败点 */
        return currentUser(userId);
    }

    /**
     * Entity 转 VO：只把前端需要的字段暴露出去
     * 这一步是关键，绝不能把 User 实体直接返回（里面有 openid）
     */
    private LoginVO.UserVO toUserVO(User user, ClassInfo clazz) {
        LoginVO.UserVO vo = new LoginVO.UserVO();
        vo.setId(user.getId());
        vo.setName(user.getName());
        vo.setRole(user.getRole());
        vo.setAvatar(user.getAvatar());
        vo.setPhone(user.getPhone());
        vo.setUnit(user.getUnit());
        if (clazz != null) {
            vo.setClassId(clazz.getId());
            vo.setClassName(clazz.getName());
        }
        return vo;
    }
}
