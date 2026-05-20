package io.github.uipilnil.backend.dm.pageCache;

import io.github.uipilnil.backend.common.AbstractCache;
import io.github.uipilnil.backend.dm.page.Page;
import io.github.uipilnil.backend.dm.page.PageImpl;
import io.github.uipilnil.backend.utils.Panic;
import io.github.uipilnil.common.Error;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 数据页管理器
 */
public class PageCacheImpl extends AbstractCache<Page> implements PageCache {
    private static final int MEM_MIN_LIM = 10; // 最小缓存页数，过小会影响性能
    public static final String DB_SUFFIX = ".db";

    private RandomAccessFile file;
    private FileChannel fc;
    private Lock fileLock;

    private AtomicInteger pageNumbers;

    PageCacheImpl(RandomAccessFile file, FileChannel fileChannel, int maxResource) {
        super(maxResource);
        if (maxResource < MEM_MIN_LIM) {
            Panic.panic(Error.MemTooSmallException);
        }
        long length = 0;
        try {
            length = file.length();
        } catch (IOException e) {
            Panic.panic(e);
        }
        this.file = file;
        this.fc = fileChannel;
        this.fileLock = new ReentrantLock();
        this.pageNumbers = new AtomicInteger((int) length / PAGE_SIZE);
    }

    /**
     * 资源不在缓存时，去磁盘加载资源
     */
    @Override
    protected Page getForCache(long key) {
        int pgno = (int) key;
        long offset = PageCacheImpl.pageOffset(pgno);

        ByteBuffer buf = ByteBuffer.allocate(PAGE_SIZE);
        fileLock.lock();
        try {
            fc.position(offset);
            fc.read(buf);
        } catch (IOException e) {
            Panic.panic(e);
        }
        fileLock.unlock();

        return new PageImpl(pgno, buf.array(), this);
    }

    /**
     * 资源移除缓存前，先写回磁盘
     */
    @Override
    protected void releaseForCache(Page pg) {
        if (pg.isDirty()) {
            flush(pg);
            pg.setDirty(false);
        }
    }

    /**
     * 新建页
     *
     * @param initData
     * @return
     */
    @Override
    public int newPage(byte[] initData) {
        int pgno = pageNumbers.incrementAndGet();
        Page pg = new PageImpl(pgno, initData, null);
        flush(pg);
        return pgno;
    }

    /**
     * 从磁盘读数据
     *
     * @param pgno
     * @return
     */
    @Override
    public Page getPage(int pgno) throws Exception {
        return get((long) pgno);
    }

    /**
     * 用完一页，归还缓存
     *
     * @param page
     */
    @Override
    public void release(Page page) {
        release((long) page.getPageNumber());
    }

    /**
     * 截断文件，保留到 maxPgno 页，后面删掉
     *
     * @param maxPgno
     */
    @Override
    public void truncateByBgno(int maxPgno) {
        long size = pageOffset(maxPgno + 1);
        try {
            file.setLength(size);
        } catch (IOException e) {
            Panic.panic(e);
        }
        pageNumbers.set(maxPgno);
    }

    /**
     * 把一页数据刷盘
     *
     * @param pg
     */
    @Override
    public void flushPage(Page pg) {
        flush(pg);
    }

    private void flush(Page pg) {
        int pgno = pg.getPageNumber();
        long offset = pageOffset(pgno);

        fileLock.lock();
        try {
            ByteBuffer buf = ByteBuffer.wrap(pg.getData());
            fc.position(offset);
            fc.write(buf); // 把数据从 JVM 拷贝到 OS 缓存
            fc.force(false); // 强制把 OS 缓存刷入物理硬件
        } catch (IOException e) {
            Panic.panic(e);
        } finally {
            fileLock.unlock();
        }
    }

    /**
     * 关闭缓存，写回所有脏页
     */
    @Override
    public void close() {
        super.close(); // 父类遍历并清空缓存，把所有脏页刷盘
        try {
            fc.close();
            file.close();
        } catch (IOException e) {
            Panic.panic(e);
        }
    }

    /**
     * 获取总页数
     *
     * @return
     */
    @Override
    public int getPageNumber() {
        return pageNumbers.intValue();
    }

    /**
     * 某数据页在文件中的起始位置
     *
     * @param pgno
     * @return
     */
    private static long pageOffset(int pgno) {
        return (pgno - 1) * PAGE_SIZE;
    }
}
