package com.banxing.classassistant.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 加入密码的哈希与校验。
 *
 * <p><b>为什么用 JDK 自带的 PBKDF2 而不是 BCrypt</b>：
 * BCrypt 在 {@code spring-security-crypto} 里，本项目没引 Spring Security。
 * 为了一个密码哈希拉进一整条安全依赖链，在离线仓库环境下还容易缺包
 * （见 engineering-notes §5 的 Maven 仓库问题），不值得。
 * PBKDF2WithHmacSHA256 是 JDK 自带的，参数选对（21 万轮 + 16 字节随机盐）同样安全。
 *
 * <p>存储格式：{@code pbkdf2$sha256$210000$<base64盐>$<base64哈希>}
 * 把算法和轮数写进密文里，将来提高轮数时老密码还能验通，不用强制所有人改密。
 *
 * <p><b>本项目最容易犯的错</b>：把明文密码写进日志。
 * 这个类只提供 hash / matches，不提供「打印密文」的方法；
 * 上层打印日志时也只能拿到 {@code $} 分隔的字符串，看不出原文。
 */
public final class PasswordHasher {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String PREFIX = "pbkdf2$sha256$";
    private static final int ITERATIONS = 210_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder B64_ENC = Base64.getEncoder().withoutPadding();
    private static final Base64.Decoder B64_DEC = Base64.getDecoder();

    private PasswordHasher() {
    }

    /** 生成密文。同一个密码每次调用结果都不同（随机盐），这是正确的 */
    public static String hash(String raw) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] dk = pbkdf2(raw.toCharArray(), salt, ITERATIONS);
        return PREFIX + ITERATIONS + "$" + B64_ENC.encodeToString(salt) + "$" + B64_ENC.encodeToString(dk);
    }

    /**
     * 校验。密文格式不对（被人手动改过库）一律返回 false，不抛异常 ——
     * 一个坏掉的密码不应该让整个「加入」接口 500。
     */
    public static boolean matches(String raw, String stored) {
        if (raw == null || stored == null) {
            return false;
        }
        String[] parts = stored.split("\\$");
        // pbkdf2 / sha256 / iterations / salt / hash
        if (parts.length != 5 || !"pbkdf2".equals(parts[0]) || !"sha256".equals(parts[1])) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[2]);
            byte[] salt = B64_DEC.decode(parts[3]);
            byte[] expected = B64_DEC.decode(parts[4]);
            byte[] actual = pbkdf2(raw.toCharArray(), salt, iterations);
            // 必须用常量时间比较：普通 Arrays.equals 会在第一个不同字节处返回，
            // 攻击者能靠响应时间差一位一位试出哈希
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (Exception e) {
            // 算法是 JDK 必备实现，走不到这里；真走到了说明环境有问题，直接抛
            throw new IllegalStateException("密码哈希失败", e);
        } finally {
            // 清掉 PBEKeySpec 里的密码副本，减少内存里留明文的时间
            spec.clearPassword();
        }
    }

    /** 仅用于排查：判断一个字符串是不是本类的密文格式（不参与业务逻辑） */
    public static boolean looksHashed(String s) {
        return s != null && s.startsWith(PREFIX);
    }
}
