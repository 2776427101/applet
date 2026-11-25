package com.banxing.classassistant.util;

import com.banxing.classassistant.common.BizException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

// 把这个类注册成 Spring Bean，交给 IoC 容器管理，才能被拦截器配置注入使用
@Component
public class JwtUtil {
    // HMAC-SHA 签名密钥，用于给 token 签名和验签
    private final SecretKey key;
    // token有效期，单位：秒
    private final long expireSeconds;
    // 通过 @Value 从 application.yml 读取配置并注入构造器参数
    public JwtUtil(@Value("${app.jwt.secret}") String secret, // 从.yml文件中找到app.jwt.secret的值给secret
                   @Value("${app.jwt.expire-seconds}") long expireSeconds) {
        // 把密钥字符串转换成字节数组后，生成 JJWT 所需的 SecretKey 对象
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
        this.expireSeconds = expireSeconds;
    }
    /**
     * 生成 JWT 令牌（登录成功后调用，返回给小程序保存）
     * @param userId 用户 ID，写入 token 的 subject
     * @param role   用户角色，作为自定义声明写入 token
     * @return 签名后的 token 字符串
     */
    public String createToken(Long userId,Integer role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))                       // 主题：存放用户 ID
                .claim("role", role)                                   // 自定义声明：存放角色
                .issuedAt(now)                                         // 签发时间
                .expiration(new Date(now.getTime() + expireSeconds * 1000)) // 过期时间 = 当前时间 + 有效期(转毫秒)
                .signWith(key)                                         // 使用密钥进行签名
                .compact();                                             // 压缩为最终 token 字符串

    }

    /**
     * 生成学生令牌。
     *
     * <p>学生没有 t_user 账号（花名册里的一行，不是注册用户），所以 subject 放的是
     * <b>studentId</b>，另外多带一个 classId。role 固定 2。
     *
     * <p>为什么必须区分 role：教师接口到处在用 {@code request.getAttribute("userId")}，
     * 如果学生 token 也往 userId 里塞一个学生 id，那学生就能以"用户 121"的身份
     * 调教师接口。拦截器按路径 + role 双向卡住，见 AuthInterceptor。
     */
    public String createStudentToken(Long studentId, Long classId) {
        return createStudentToken(studentId, classId, null, null);
    }

    /**
     * 带上学号和姓名的学生令牌。
     *
     * <p>为什么要多塞这两个字段：学生登录已经不再依赖花名册（v0.41），
     * 一个还没被导入名单的人也能拿到 token。那种 token 里没有 studentId，
     * 唯一能证明"我是谁"的就是学号和姓名 —— 它们必须跟着 token 走，
     * 否则加入班级时后端不知道要给谁建花名册行。
     */
    public String createStudentToken(Long studentId, Long classId, String studentNo, String name) {
        Date now = new Date();
        var builder = Jwts.builder()
                .subject(String.valueOf(studentId))
                .claim("role", 2)
                .claim("classId", classId)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireSeconds * 1000));
        if (studentNo != null) {
            builder.claim("studentNo", studentNo);
        }
        if (name != null) {
            builder.claim("name", name);
        }
        return builder.signWith(key).compact();
    }

    /**
     * 「还没进班」的学生令牌。
     *
     * <p>subject 放 0 而不是真实 id：这个人此刻在 t_student 里<b>没有行</b>，
     * 编一个 id 出来，将来只要有人拿它去查库就会查到一个不存在的人，
     * 或者更糟 —— 查到一个别人的行（自增从 1 开始，0 永远空着）。
     *
     * <p>没有 classId claim。拦截器据此把这张 token 限制在"入班"和"我是谁"
     * 两个接口上（见 AuthInterceptor），其余接口一律 1009。
     * 这样即使前端跳错了页面，学生也看不到任何班级数据。
     */
    public String createGuestStudentToken(String studentNo, String name) {
        Date now = new Date();
        return Jwts.builder()
                .subject("0")
                .claim("role", 2)
                .claim("studentNo", studentNo)
                .claim("name", name)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireSeconds * 1000))
                .signWith(key)
                .compact();
    }

    /**
     * 解析并验证 token，返回其中的用户 ID
     * @param token 待校验的 JWT 字符串
     * @return 解析出的用户 ID
     */
    public Long parseUserId(String token) {
        return Long.valueOf(parseClaims(token).getSubject());
    }

    /**
     * 解析并验证 token，返回完整载荷。拦截器用它一次拿到 role / classId，
     * 不用为了三个字段解析三遍。
     */
    public Claims parseClaims(String token) {
        try{
            // 用同一把密钥验签，解析签名载荷
            return Jwts.parser()
                    .verifyWith(key)                 // 告诉它用哪把密钥来验
                    .build()                // 构建解析器
                    .parseSignedClaims(token)        // 解析并校验签名
                    .getPayload();                   // 获取载荷内容
        } catch (JwtException | IllegalArgumentException e) {
            // token 非法、被篡改或已过期时，统一抛出业务异常提示重新登录
            throw new BizException(1002,"登录已过期，请重新登录");
        }
    }
}
