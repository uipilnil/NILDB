package io.github.uipilnil.backend.vm;

import io.github.uipilnil.common.Error;

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 锁管理器
 */
public class LockTable {
    private Map<Long, List<Long>> x2u;     // 某事务持有的资源的 UID 列表
    private Map<Long, Long> u2x;           // 资源被事务持有
    private Map<Long, List<Long>> wait;     // 正在等待某资源的事务列表
    private Map<Long, Lock> waitLock;       // 正在等待资源的事务所持有的锁
    private Map<Long, Long> waitU;          // 事务正在等待的资源
    private Lock lock;

    public LockTable() {
        x2u = new HashMap<>();
        u2x = new HashMap<>();
        wait = new HashMap<>();
        waitLock = new HashMap<>();
        waitU = new HashMap<>();
        lock = new ReentrantLock();
    }

    /**
     * 事务尝试获取资源
     *
     * @param xid
     * @param uid
     * @return
     */
    public Lock add(long xid, long uid) throws Exception {
        lock.lock();
        try {
            // 事务已经持有资源
            if (isInList(x2u, xid, uid)) {
                return null;
            }

            // 资源不被其他事务持有
            if (!u2x.containsKey(uid)) {
                u2x.put(uid, xid);
                putIntoList(x2u, xid, uid);
                return null;
            }

            // 资源被占用，当前事务等待资源
            waitU.put(xid, uid);
            putIntoList(wait, uid, xid);

            if (hasDeadLock()) {
                waitU.remove(xid);
                removeFromList(wait, uid, xid);
                throw Error.DeadlockException;
            }

            Lock l = new ReentrantLock();
            l.lock();
            waitLock.put(xid, l);
            return l;

        } finally {
            lock.unlock();
        }
    }

    /**
     * 事务结束（提交或回滚），释放资源
     *
     * @param xid
     */
    public void remove(long xid) {
        lock.lock();
        try {
            List<Long> l = x2u.get(xid);
            if (l != null) {
                while (l.size() > 0) {
                    Long uid = l.remove(0);
                    selectNewXID(uid);
                }
            }

            x2u.remove(xid);
            waitU.remove(xid);
            waitLock.remove(xid);

        } finally {
            lock.unlock();
        }
    }

    /**
     * 资源被释放后，从等待队列中选择一个事务来占用资源
     *
     * @param uid
     */
    private void selectNewXID(long uid) {
        u2x.remove(uid);

        List<Long> l = wait.get(uid);
        if (l == null) return;
        assert l.size() > 0;

        while (l.size() > 0) {
            long xid = l.remove(0);
            if (!waitLock.containsKey(xid)) {
                continue;
            } else {
                u2x.put(uid, xid);
                Lock lo = waitLock.remove(xid);
                waitU.remove(xid);
                lo.unlock();
                break;
            }
        }

        if (l.size() == 0) wait.remove(uid);
    }

    /**
     * 判断某元素是否在以 uid0 为键的列表中
     *
     * @param listMap
     * @param uid0
     * @param uid1
     * @return
     */
    private boolean isInList(Map<Long, List<Long>> listMap, long uid0, long uid1) {
        List<Long> l = listMap.get(uid0);
        if (l == null) return false;
        Iterator<Long> i = l.iterator();
        while (i.hasNext()) {
            long e = i.next();
            if (e == uid1) {
                return true;
            }
        }
        return false;
    }

    /**
     * 把某元素插入到以 uid0 为键的列表头部
     *
     * @param listMap
     * @param uid0
     * @param uid1
     */
    private void putIntoList(Map<Long, List<Long>> listMap, long uid0, long uid1) {
        if (!listMap.containsKey(uid0)) {
            listMap.put(uid0, new ArrayList<>());
        }
        listMap.get(uid0).add(0, uid1);
    }

    /**
     * 在以 uid0 为键的列表中。删除第一个等于 uid1 的元素
     *
     * @param listMap
     * @param uid0
     * @param uid1
     */
    private void removeFromList(Map<Long, List<Long>> listMap, long uid0, long uid1) {
        List<Long> l = listMap.get(uid0);
        if (l == null) return;
        Iterator<Long> i = l.iterator();
        while (i.hasNext()) {
            long e = i.next();
            if (e == uid1) {
                i.remove();
                break;
            }
        }

        if (l.size() == 0) {
            listMap.remove(uid0);
        }
    }

    private Map<Long, Integer> xidStamp;    // 事务被哪个轮次的 DFS 访问过
    private int stamp;                      // 当前 DFS 轮次的编号

    /**
     * 基于等待图实现死锁检测
     * 节点：事务
     * 有向边：A -> B 表示 A 等待 B 的资源
     *
     * @return
     */
    private boolean hasDeadLock() {
        xidStamp = new HashMap<>();
        stamp = 1; // 起始编号：1 未使用
        for (long xid : x2u.keySet()) {
            // 事务被标记过，跳过
            Integer s = xidStamp.get(xid);
            if (s != null && s > 0) {
                continue;
            }

            stamp++;
            if (dfs(xid)) {
                return true;
            }
        }
        return false;
    }

    private boolean dfs(long xid) {
        Integer stp = xidStamp.get(xid);
        // 本轮访问过，说明回到起点，形成环
        if (stp != null && stp == stamp) {
            return true;
        }
        // 之前轮次访问过，说明无环
        if (stp != null && stp < stamp) {
            return false;
        }
        // 标记本轮已访问
        xidStamp.put(xid, stamp);

        Long uid = waitU.get(xid);
        // 无事务等待：无环
        if (uid == null) return false;
        // 找出占用本资源的事务，递归搜索
        Long x = u2x.get(uid);
        assert x != null;
        return dfs(x);
    }
}
