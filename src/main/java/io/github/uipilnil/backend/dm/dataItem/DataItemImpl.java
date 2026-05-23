package io.github.uipilnil.backend.dm.dataItem;

import io.github.uipilnil.backend.common.SubArray;
import io.github.uipilnil.backend.dm.DataManagerImpl;
import io.github.uipilnil.backend.dm.page.Page;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 数据项管理器
 * 数据项结构：[ValidFlag] [DataSize] [Data]
 * ValidFlag：合法标志位，0 合法，1 非法，1 字节
 * DataSize：数据长度，2 字节
 */
public class DataItemImpl implements DataItem {
    // 数据项内部字段的相对偏移量
    static final int OF_VALID = 0;
    static final int OF_SIZE = 1;
    static final int OF_DATA = 3;

    private SubArray raw;
    private byte[] oldRaw;
    private Lock rLock;
    private Lock wLock;
    private DataManagerImpl dm;
    private long uid;
    private Page pg;

    public DataItemImpl(SubArray raw, byte[] oldRaw, Page pg, long uid, DataManagerImpl dm) {
        this.raw = raw;
        this.oldRaw = oldRaw;
        ReadWriteLock lock = new ReentrantReadWriteLock();
        rLock = lock.readLock();
        wLock = lock.writeLock();
        this.dm = dm;
        this.uid = uid;
        this.pg = pg;
    }

    /**
     * 校验合法性
     *
     * @return
     */
    public boolean isValid() {
        return raw.raw[raw.start + OF_VALID] == (byte) 0;
    }

    /**
     * 获取数据项中的数据
     *
     * @return
     */
    @Override
    public SubArray data() {
        return new SubArray(raw.raw, raw.start + OF_DATA, raw.end);
    }

    /**
     * 写前准备（防崩溃 + 支持事务回滚）
     */
    @Override
    public void before() {
        wLock.lock();
        pg.setDirty(true);
        System.arraycopy(raw.raw, raw.start, oldRaw, 0, oldRaw.length);
    }

    /**
     * 回滚，撤销写操作
     */
    @Override
    public void unBefore() {
        System.arraycopy(oldRaw, 0, raw.raw, raw.start, oldRaw.length);
        wLock.unlock();
    }

    /**
     * 提交写操作，记录 WAL
     *
     * @param xid
     */
    @Override
    public void after(long xid) {
        dm.logDataItem(xid, this);
        wLock.unlock();
    }

    /**
     * 释放数据项资源
     */
    @Override
    public void release() {
        dm.releaseDataItem(this);
    }

    /**
     * 对数据项加写锁
     */
    @Override
    public void lock() {
        wLock.lock();
    }

    /**
     * 释放数据项的写锁
     */
    @Override
    public void unlock() {
        wLock.unlock();
    }

    /**
     * 对数据项加读锁
     */
    @Override
    public void rLock() {
        rLock.lock();
    }

    /**
     * 释放数据项的读锁
     */
    @Override
    public void rUnLock() {
        rLock.unlock();
    }

    /**
     * 获取数据项所在页对象
     *
     * @return
     */
    @Override
    public Page page() {
        return pg;
    }

    /**
     * 获取数据项的 UID
     *
     * @return
     */
    @Override
    public long getUid() {
        return uid;
    }

    /**
     * 获取修改前的历史快照数据
     *
     * @return
     */
    @Override
    public byte[] getOldRaw() {
        return oldRaw;
    }

    /**
     * 获取完整数据
     *
     * @return
     */
    @Override
    public SubArray getRaw() {
        return raw;
    }
}
