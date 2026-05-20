package io.github.uipilnil.backend.dm.pageCache;

import io.github.uipilnil.backend.common.AbstractCache;
import io.github.uipilnil.backend.dm.page.Page;

import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;

/**
 * 数据页管理器
 */
public class PageCacheImpl extends AbstractCache<Page> implements PageCache {
    public static final String DB_SUFFIX = ".db";

    private RandomAccessFile file;
    private FileChannel fc;

    PageCacheImpl(RandomAccessFile file, FileChannel fileChannel, int maxResource) {
        this.file = file;
        this.fc = fileChannel;
    }

    int newPage(byte[] initData) {
    }

    /**
     * 从磁盘读数据
     *
     * @param pgno
     * @return
     */
    Page getPage(int pgno) {
    }

    /**
     * 用完一页，归还缓存
     *
     * @param page
     */
    void release(Page page) {
    }

    /**
     * 关闭缓存，关闭文件，刷回所有脏页
     */
    void close() {
    }

    /**
     * 截断文件，保留到 maxPgno 页，后面删掉
     *
     * @param maxPgno
     */
    void truncateByBgno(int maxPgno) {
    }

    int getPageNumber() {
    }

    /**
     * 把一页强制刷盘
     *
     * @param pg
     */
    void flushPage(Page pg) {
    }
}
