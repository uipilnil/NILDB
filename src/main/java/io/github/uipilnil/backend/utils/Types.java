package io.github.uipilnil.backend.utils;

/**
 * UID 生成器
 */
public class Types {
    public static long addressToUid(int pgno, short offset) {
        long u0 = (long) pgno;
        long u1 = (long) offset;
        return u0 << 32 | u1;
    }
}
