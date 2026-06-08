package io.github.uipilnil.backend.vm;

import io.github.uipilnil.backend.tm.TransactionManager;

/**
 * 可见性判断器
 * 判断数据版本对当前事务是否可见
 */
public class Visibility {
    /**
     * 可见性判断的入口
     * 根据事务隔离级别，分流到不同判断逻辑
     *
     * @param tm
     * @param t
     * @param e
     * @return
     */
    public static boolean isVisible(TransactionManager tm, Transaction t, Entry e) {
        if (t.level == 0) {
            return readCommitted(tm, t, e);
        } else {
            return repeatableRead(tm, t, e);
        }
    }

    /**
     * 在可重复读 RR 级别下，判断当前记录是否被新版本覆盖
     *
     * @param tm
     * @param t
     * @param e
     * @return
     */
    public static boolean isVersionSkip(TransactionManager tm, Transaction t, Entry e) {
        long xmax = e.getXmax();
        if (t.level == 0) {
            return false;
        } else {
            return tm.isCommitted(xmax) && (xmax > t.xid || t.isInSnapshot(xmax));
        }
    }

    /**
     * 可见性判断 - 读提交 RC
     *
     * @param tm
     * @param t
     * @param e
     * @return
     */
    private static boolean readCommitted(TransactionManager tm, Transaction t, Entry e) {
        long xid = t.xid;
        long xmin = e.getXmin();
        long xmax = e.getXmax();

        if (xmin == xid && xmax == 0) return true;

        if (tm.isCommitted(xmin)) {
            if (xmax == 0) return true;

            // 删除者不是当前事务，且删除未提交
            if (xmax != xid) {
                if (!tm.isCommitted(xmax)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 可见性判断 - 可重复读 RR
     *
     * @param tm
     * @param t
     * @param e
     * @return
     */
    private static boolean repeatableRead(TransactionManager tm, Transaction t, Entry e) {
        long xid = t.xid;
        long xmin = e.getXmin();
        long xmax = e.getXmax();

        if (xmin == xid && xmax == 0) return true;

        if (tm.isCommitted(xmin) && xmin < xid && !t.isInSnapshot(xmin)) {
            if (xmax == 0) return true;
            if (xmax != xid) {
                if (!tm.isCommitted(xmax) || xmax > xid || t.isInSnapshot(xmax)) {
                    return true;
                }
            }
        }
        return false;
    }
}
