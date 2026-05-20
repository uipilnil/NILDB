package io.github.uipilnil.backend.dm.page;

import io.github.uipilnil.backend.dm.pageCache.PageCache;
import io.github.uipilnil.backend.utils.Parser;

import java.util.Arrays;

/**
 * 普通页管理器
 * 普通页结构：[FreeSpaceOffset] [Data]
 * FreeSpaceOffset：空闲空间的起始位置，用 2 字节存储
 */
public class PageX {
    private static final short OF_FREE = 0;
    private static final short OF_DATA = 2;
    public static final int MAX_FREE_SPACE = PageCache.PAGE_SIZE - OF_DATA;

    /**
     * 初始化空白页
     *
     * @return
     */
    public static byte[] initRaw() {
        byte[] raw = new byte[PageCache.PAGE_SIZE];
        setFSO(raw, OF_DATA);
        return raw;
    }

    /**
     * 设置空闲空间的起始位置
     *
     * @param raw
     * @param ofData
     */
    private static void setFSO(byte[] raw, short ofData) {
        System.arraycopy(Parser.shortToByte(ofData), 0, raw, OF_FREE, OF_DATA);
    }

    /**
     * 获取空闲空间的起始位置
     *
     * @param pg
     * @return
     */
    public static short getFSO(Page pg) {
        return getFSO(pg.getData());
    }

    private static short getFSO(byte[] raw) {
        return Parser.parseShort(Arrays.copyOfRange(raw, 0, 2));
    }

    /**
     * 把数据插入页里
     *
     * @param pg
     * @param raw
     * @return 插入位置
     */
    public static short insert(Page pg, byte[] raw) {
        pg.setDirty(true);
        short offset = getFSO(pg.getData());
        System.arraycopy(raw, 0, pg.getData(), offset, raw.length);
        setFSO(pg.getData(), (short) (offset + raw.length));
        return offset;
    }

    /**
     * 获取页面的空闲空间大小
     *
     * @param pg
     * @return
     */
    public static int getFreeSpace(Page pg) {
        return PageCache.PAGE_SIZE - (int) getFSO(pg.getData());
    }

    /**
     * 崩溃恢复插入
     * 把数据插入到页的空闲空间起始位置，并更新空闲空间起始位置
     *
     * @param pg
     * @param raw
     * @param offset
     */
    public static void recoverInsert(Page pg, byte[] raw, short offset) {
        pg.setDirty(true);
        System.arraycopy(raw, 0, pg.getData(), offset, raw.length);

        short rawFSO = getFSO(pg.getData());
        // 如果恢复的是位于中间的旧数据，不需要修改空闲空间的起始位置
        if (rawFSO < offset + raw.length) {
            setFSO(pg.getData(), (short) (offset + raw.length));
        }
    }

    /**
     * 崩溃恢复更新
     * 把数据插入到页的空闲空间起始位置，不更新空闲空间起始位置
     *
     * @param pg
     * @param raw
     * @param offset
     */
    public static void recoverUpdate(Page pg, byte[] raw, short offset) {
        pg.setDirty(true);
        System.arraycopy(raw, 0, pg.getData(), offset, raw.length);
    }
}
