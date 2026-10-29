package com.banxing.classassistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.banxing.classassistant.common.BizException;
import com.banxing.classassistant.dto.LoginVO;
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
        // 第四步：查他带的班（创建一个"查询条件构造器"，用equal方法设定headTeacherId = 当前用户）
        ClassInfo clazz = classMapper.selectOne(
                new LambdaQueryWrapper<ClassInfo>().eq(ClassInfo::getHeadTeacherId, user.getId()));
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
        ClassInfo clazz = classMapper.selectOne(
                new LambdaQueryWrapper<ClassInfo>().eq(ClassInfo::getHeadTeacherId, user.getId()));
        return toUserVO(user, clazz);
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
        if (clazz != null) {
            vo.setClassId(clazz.getId());
            vo.setClassName(clazz.getName());
        }
        return vo;
    }
}
