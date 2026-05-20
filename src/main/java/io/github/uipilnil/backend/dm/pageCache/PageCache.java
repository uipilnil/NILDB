package io.github.uipilnil.backend.dm.pageCache;

import io.github.uipilnil.backend.dm.page.Page;
import io.github.uipilnil.backend.utils.Panic;
import io.github.uipilnil.common.Error;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;

public interface PageCache {
    // 页大小 8KB
    public static final int PAGE_SIZE = 1 << 13;

    int newPage(byte[] initData);

    Page getPage(int pgno) throws Exception;

    void release(Page page);

    void close();

    void truncateByBgno(int maxPgno);

    void flushPage(Page pg);

    int getPageNumber();

    /**
     * 新建数据库文件
     *
     * @param path
     * @param memory
     * @return
     */
    public static PageCacheImpl create(String path, long memory) {
        File f = new File(path + PageCacheImpl.DB_SUFFIX);
        try {
            if (!f.createNewFile()) {
                Panic.panic(Error.FileExistsException);
            }
        } catch (Exception e) {
            Panic.panic(e);
        }
        if (!f.canRead() || !f.canWrite()) {
            Panic.panic(Error.FileCannotRWException);
        }

        RandomAccessFile raf = null;
        FileChannel fc = null;
        try {
            // 打开文件，拿到读写权限
            raf = new RandomAccessFile(f, "rw");
            // 从 raf 里取出一个文件通道，支持随时修改文件任意位置。后续用它记录事务状态
            fc = raf.getChannel();
        } catch (FileNotFoundException e) {
            Panic.panic(e);
        }

        return new PageCacheImpl(raf, fc, (int) memory / PAGE_SIZE);
    }

    /**
     * 打开数据库文件
     *
     * @param path
     * @param memory
     * @return
     */
    public static PageCacheImpl open(String path, long memory) {
        File f = new File(path + PageCacheImpl.DB_SUFFIX);
        if (!f.exists()) {
            Panic.panic(Error.FileNotExistsException);
        }
        if (!f.canRead() || !f.canWrite()) {
            Panic.panic(Error.FileCannotRWException);
        }

        RandomAccessFile raf = null;
        FileChannel fc = null;
        try {
            raf = new RandomAccessFile(f, "rw");
            fc = raf.getChannel();
        } catch (FileNotFoundException e) {
            Panic.panic(e);
        }

        return new PageCacheImpl(raf, fc, (int) memory / PAGE_SIZE);
    }
}
