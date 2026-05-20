package io.github.uipilnil.backend.dm.page;

import io.github.uipilnil.backend.dm.pageCache.PageCache;
import io.github.uipilnil.backend.utils.RandomUtil;

import java.util.Arrays;

/**
 * 第一页管理器
 * 判断数据库是否正常关闭
 */
public class PageOne {
    private static final int OF_VC = 100;
    private static final int LEN_VC = 8;

    /**
     * 初始化第一页
     *
     * @return
     */
    public static byte[] InitRaw() {
        byte[] raw = new byte[PageCache.PAGE_SIZE];
        setVcOpen(raw);
        return raw;
    }

    /**
     * 数据库启动时给 100-107 字节处填入随机数据，作为启动校验码
     *
     * @param pg
     */
    public static void setVcOpen(Page pg) {
        pg.setDirty(true);
        setVcOpen(pg.getData());
    }

    private static void setVcOpen(byte[] raw) {
        System.arraycopy(RandomUtil.randomBytes(LEN_VC), 0, raw, OF_VC, LEN_VC);
    }

    /**
     * 数据库关闭时把启动校验码拷贝到 108-115 字节处
     *
     * @param pg
     */
    public static void setVcClose(Page pg) {
        pg.setDirty(true);
        setVcClose(pg.getData());
    }

    private static void setVcClose(byte[] raw) {
        System.arraycopy(raw, OF_VC, raw, OF_VC + LEN_VC, LEN_VC);
    }

    /**
     * 数据库启动时，判断两处校验码是否一致，从而判断数据库是否正常关闭
     * @param pg
     * @return
     */
    public static boolean checkVc(Page pg) {
        return checkVc(pg.getData());
    }

    private static boolean checkVc(byte[] raw) {
        return Arrays.equals(Arrays.copyOfRange(raw, OF_VC, OF_VC + LEN_VC), Arrays.copyOfRange(raw, OF_VC + LEN_VC, OF_VC + 2 * LEN_VC));
    }
}
