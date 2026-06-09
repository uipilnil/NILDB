package io.github.uipilnil.backend.vm;

import io.github.uipilnil.backend.tm.TransactionManagerImpl;

import java.util.HashMap;
import java.util.Map;

/**
 * 事务信息管理器
 */
public class Transaction {
    public long xid;
    /**
     * 0：读提交 RC
     * 1：可重复读 RR
     */
    public int level;                      // 隔离级别
    public Map<Long, Boolean> snapshot;     // 事务创建时的快照 key 是活跃事务的 xid 记录当前事务启动时的活跃事务
    public Exception err;                   // 记录事务执行过程中出现的异常
    public boolean autoAborted;             // 记录事务是否被系统自动回滚

    /**
     * 创建事务
     *
     * @param xid
     * @param level
     * @param active
     * @return
     */
    public static Transaction newTransaction(long xid, int level, Map<Long, Transaction> active) {
        Transaction t = new Transaction();
        t.xid = xid;
        t.level = level;
        // 给活跃事务拍一个快照
        if (level != 0) {
            t.snapshot = new HashMap<>();
            for (Long x : active.keySet()) {
                t.snapshot.put(x, true);
            }
        }
        return t;
    }

    /**
     * 判断事务是否在快照中
     *
     * @param xid
     * @return
     */
    public boolean isInSnapshot(long xid) {
        if (xid == TransactionManagerImpl.SUPER_XID) {
            return false;
        }
        return snapshot.containsKey(xid);
    }
}
