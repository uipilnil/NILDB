package io.github.uipilnil.backend.dm.page;

import io.github.uipilnil.backend.dm.pageCache.PageCache;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 数据页
 * 一个数据页中有多个数据项：
 * [start1 数据项1 end1] [start2 数据项2 end2] [start3 数据项3 end3] ...
 */
public class PageImpl implements Page {
    private int pageNumber;
    private boolean dirty;
    private byte[] data;
    private Lock lock;

    private PageCache pc;

    public PageImpl(int pageNumber, byte[] data, PageCache pc) {
        this.pageNumber = pageNumber;
        this.data = data;
        this.pc = pc;
        lock = new ReentrantLock();
    }

    /**
     * 加锁保证并发安全
     */
    @Override
    public void lock() {
        lock.lock();
    }

    @Override
    public void unlock() {
        lock.unlock();
    }

    /**
     * 把页还给缓存池，释放资源
     */
    @Override
    public void release() {
        pc.release(this);
    }

    /**
     * 设置脏页
     *
     * @param dirty
     */
    @Override
    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    @Override
    public boolean isDirty() {
        return dirty;
    }

    @Override
    public int getPageNumber() {
        return pageNumber;
    }

    @Override
    public byte[] getData() {
        return data;
    }
}
