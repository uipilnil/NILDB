package io.github.uipilnil.backend.dm.page;

import io.github.uipilnil.backend.dm.pageCache.PageCache;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 数据页
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
    public void lock() {
        lock.lock();
    }

    public void unlock() {
        lock.unlock();
    }

    /**
     * 把页还给缓存池，释放资源
     */
    public void release() {
        pc.release(this);
    }

    /**
     * 设置脏页
     *
     * @param dirty
     */
    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    public boolean isDirty() {
        return dirty;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public byte[] getData() {
        return data;
    }
}
