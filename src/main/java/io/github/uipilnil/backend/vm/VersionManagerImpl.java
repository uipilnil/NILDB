package io.github.uipilnil.backend.vm;

import io.github.uipilnil.backend.common.AbstractCache;
import io.github.uipilnil.backend.dm.DataManager;
import io.github.uipilnil.backend.tm.TransactionManager;
import io.github.uipilnil.backend.tm.TransactionManagerImpl;
import io.github.uipilnil.backend.utils.Panic;
import io.github.uipilnil.common.Error;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 版本管理器
 */
public class VersionManagerImpl extends AbstractCache<Entry> implements VersionManager {
    TransactionManager tm;
    DataManager dm;
    Map<Long, Transaction> activeTransaction;
    Lock lock;
    LockTable lt;

    public VersionManagerImpl(TransactionManager tm, DataManager dm) {
        super(0);
        this.tm = tm;
        this.dm = dm;
        this.activeTransaction = new HashMap<>();
        activeTransaction.put(TransactionManagerImpl.SUPER_XID, Transaction.newTransaction(TransactionManagerImpl.SUPER_XID, 0, null));
        this.lock = new ReentrantLock();
        this.lt = new LockTable();
    }


    @Override
    public byte[] read(long xid, long uid) throws Exception {
        lock.lock();
        Transaction t = activeTransaction.get(xid);
        lock.unlock();

        if (t.err != null) {
            throw t.err;
        }

        Entry entry = null;
        try {
            entry = super.get(uid);
        } catch (Exception e) {
            if (e == Error.NullEntryException) {
                return null;
            } else {
                throw e;
            }
        }
        try {
            if (Visibility.isVisible(tm, t, entry)) {
                return entry.data();
            } else {
                return null;
            }
        } finally {
            entry.release();
        }
    }

    @Override
    public long insert(long xid, byte[] data) throws Exception {
        lock.lock();
        Transaction t = activeTransaction.get(xid);
        lock.unlock();

        if (t.err != null) {
            throw t.err;
        }

        byte[] raw = Entry.wrapEntryRaw(xid, data);
        return dm.insert(xid, raw);
    }

    @Override
    public boolean delete(long xid, long uid) throws Exception {
        lock.lock();
        Transaction t = activeTransaction.get(xid);
        lock.unlock();

        if (t.err != null) {
            throw t.err;
        }
        Entry entry = null;
        try {
            entry = super.get(uid);
        } catch (Exception e) {
            if (e == Error.NullEntryException) {
                return false;
            } else {
                throw e;
            }
        }
        try {
            if (!Visibility.isVisible(tm, t, entry)) {
                return false;
            }
            Lock l = null;
            try {
                l = lt.add(xid, uid);
            } catch (Exception e) {
                t.err = Error.ConcurrentUpdateException;
                internAbort(xid, true);
                t.autoAborted = true;
                throw t.err;
            }
            if (l != null) {
                l.lock();
                l.unlock();
            }

            // 重复删除检查
            if (entry.getXmax() == xid) {
                return false;
            }

            if (Visibility.isVersionSkip(tm, t, entry)) {
                t.err = Error.ConcurrentUpdateException;
                internAbort(xid, true);
                t.autoAborted = true;
                throw t.err;
            }

            entry.setXmax(xid);
            return true;

        } finally {
            entry.release();
        }
    }

    @Override
    public long begin(int level) {
        lock.lock();
        try {
            long xid = tm.begin();
            Transaction t = Transaction.newTransaction(xid, level, activeTransaction);
            activeTransaction.put(xid, t);
            return xid;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void commit(long xid) throws Exception {
        lock.lock();
        Transaction t = activeTransaction.get(xid);
        lock.unlock();

        try {
            if (t.err != null) {
                throw t.err;
            }
        } catch (NullPointerException n) {
            System.out.println(xid);
            System.out.println(activeTransaction.keySet());
            Panic.panic(n);
        }

        lock.lock();
        activeTransaction.remove(xid);
        lock.unlock();

        lt.remove(xid);
        tm.commit(xid);
    }

    @Override
    public void abort(long xid) {
        internAbort(xid, false);
    }

    /**
     * 释放记录的引用
     *
     * @param entry
     */
    public void releaseEntry(Entry entry) {
        super.release(entry.getUid());
    }

    /**
     * 回滚
     *
     * @param xid
     * @param autoAborted
     */
    private void internAbort(long xid, boolean autoAborted) {
        lock.lock();
        Transaction t = activeTransaction.get(xid);

        if (!autoAborted) {
            activeTransaction.remove(xid);
        }
        lock.unlock();

        if (t.autoAborted) return;
        lt.remove(xid);
        tm.abort(xid);
    }

    /**
     * 把记录从磁盘加载到缓存
     *
     * @param uid
     * @return
     * @throws Exception
     */
    @Override
    protected Entry getForCache(long uid) throws Exception {
        Entry entry = Entry.loadEntry(this, uid);
        if (entry == null) {
            throw Error.NullEntryException;
        }
        return entry;
    }

    /**
     * 释放记录占用的缓存资源
     *
     * @param entry
     */
    @Override
    protected void releaseForCache(Entry entry) {
        entry.remove();
    }
}
