package com.banxing.classassistant.util;

import java.security.SecureRandom;

/**
 * 邀请码生成与规范化。
 *
 * <p>字符集只有 32 个字符：数字 2-9 去掉 0 和 1，字母 A-Z 去掉 I 和 O。
 * 原因很实际：邀请码要靠嘴念、靠手抄、靠微信截图转发，
 * {@code 0/O}、{@code 1/I/l} 这三组混起来之后，老师自己也分不清是哪个。
 * 全大写而不是大小写混合，是为了让「输入时统一转大写」这种处理成立 ——
 * 混合大小写的话 {@code k7m2} 和 {@code K7M2} 是两个不同的码，用户永远在猜。
 *
 * <p>32^10 ≈ 1.13×10^15 种组合，撞码概率可以忽略；但仍然靠数据库唯一键兜底，
 * 不靠概率（见 ContainerServiceImpl#insertWithUniqueCode）。
 */
public final class InviteCodeGenerator {

    /** 邀请码字符集：32 个，排除 0 O 1 I */
    private static final char[] ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();

    /** 邀请码长度，需求写死 10 位 */
    public static final int CODE_LENGTH = 10;

    /**
     * SecureRandom 是线程安全的，做成静态常量复用。
     * 不用 Random：种子可预测，等于把「猜邀请码」变成一道简单题。
     */
    private static final SecureRandom RANDOM = new SecureRandom();

    private InviteCodeGenerator() {
    }

    /** 生成一个新的 10 位邀请码 */
    public static String next() {
        char[] buf = new char[CODE_LENGTH];
        for (int i = 0; i < CODE_LENGTH; i++) {
            buf[i] = ALPHABET[RANDOM.nextInt(ALPHABET.length)];
        }
        return new String(buf);
    }

    /**
     * 规范化用户输入：去首尾空格、去掉中间所有空白、转大写。
     *
     * <p>用户从微信里复制邀请码时经常带空格（「K7M2 QX9B 4T」），
     * 直接比对会判成无效，体验很差。这里统一清洗，后面只处理一种形态。
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("\\s+", "").toUpperCase();
    }

    /**
     * 校验格式：长度 10 且每个字符都在字符集内。
     *
     * <p>先校验格式再查库，可以把「用户少输一位」和「码真的不存在」区分开，
     * 也避免拿超长字符串去查库。
     */
    public static boolean isValidFormat(String code) {
        if (code == null || code.length() != CODE_LENGTH) {
            return false;
        }
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            boolean ok = false;
            for (char a : ALPHABET) {
                if (a == c) {
                    ok = true;
                    break;
                }
            }
            if (!ok) {
                return false;
            }
        }
        return true;
    }
}
