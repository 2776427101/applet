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
     * 解析并验证 token，返回其中的用户 ID
     * @param token 待校验的 JWT 字符串
     * @return 解析出的用户 ID
     */
    public Long parseUserId(String token) {
        try{
            // 用同一把密钥验签，解析签名载荷
            Claims claims = Jwts.parser()
                    .verifyWith(key)                 // 告诉它用哪把密钥来验
                    .build()                // 构建解析器
                    .parseSignedClaims(token)        // 解析并校验签名
                    .getPayload();                   // 获取载荷内容
            // 从 subject 中还原用户 ID（字符串转 Long）
            return Long.valueOf(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            // token 非法、被篡改或已过期时，统一抛出业务异常提示重新登录
            throw new BizException(1002,"登录已过期，请重新登录");
        }
    }
}
