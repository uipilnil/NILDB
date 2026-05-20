package io.github.uipilnil.backend.utils;

import java.security.SecureRandom;
import java.util.Random;

/**
 * 生成随机字节数组工具类
 */
public class RandomUtil {
    public static byte[] randomBytes(int length) {
        Random r = new SecureRandom();
        byte[] buf = new byte[length];
        r.nextBytes(buf);
        return buf;
    }
}
