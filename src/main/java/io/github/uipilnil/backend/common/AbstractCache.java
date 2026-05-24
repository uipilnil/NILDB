package io.github.uipilnil.backend.common;

import io.github.uipilnil.common.Error;

import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 缓存框架，管理缓存资源
 *
 * @param <T>
 */
public abstract class AbstractCache<T> {
    private HashMap<Long, T> cache;            // 缓存中的资源 <页号, 数据>
    private HashMap<Long, Integer> references; // 某页的引用计数
    private HashMap<Long, Boolean> getting;    // 正在被从磁盘加载到缓存的资源

    private int maxResource;                   // 缓存的最大缓存资源数
    private int count = 0;                     // 缓存中资源的个数
    private Lock lock;                         // 锁住缓存结构（而非资源）

    public AbstractCache(int maxResource) {
        this.maxResource = maxResource;

        cache = new HashMap<>();
        references = new HashMap<>();
        getting = new HashMap<>();

        lock = new ReentrantLock();
    }

    /**
     * 获取缓存资源
     *
     * @param key
     * @return
     */
    protected T get(long key) throws Exception {
        // 尝试获取资源
        while (true) {
            lock.lock();

            // 请求的资源被其他线程持有
            if (getting.containsKey(key)) {
                lock.unlock();
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    continue;
                }
                continue;
            }

            // 资源在缓存中，直接返回
            if (cache.containsKey(key)) {
                T obj = cache.get(key);
                references.put(key, references.get(key) + 1);
                lock.unlock();
                return obj;
            }

            // 资源不在缓存中，且缓存已满
            if (maxResource > 0 && count == maxResource) {
                lock.unlock();
                throw Error.CacheFullException;
            }

            // 缓存未命中，如首次访问某资源
            count++; // 抢占位置
            getting.put(key, true);
            lock.unlock();
            break;
        }

        // 资源不在缓存时，去磁盘加载资源
        T obj = null;
        try {
            obj = getForCache(key);
        } catch (Exception e) {
            // 磁盘加载失败时执行回滚
            lock.lock();
            count--;
            getting.remove(key);
            lock.unlock();
            throw e;
        }

        // 资源加载成功后，写入缓存
        lock.lock();
        getting.remove(key);
        cache.put(key, obj);
        references.put(key, 1);
        lock.unlock();

        return obj;
    }

    /**
     * 减少引用计数，为零则释放缓存
     *
     * @param key
     */
    protected void release(long key) {
        lock.lock();
        try {
            int ref = references.get(key) - 1;
            if (ref == 0) {
                T obj = cache.get(key);
                releaseForCache(obj);
                references.remove(key);
                cache.remove(key);
                count--;
            } else {
                references.put(key, ref);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * 关闭缓存，把所有资源写回磁盘
     */
    protected void close() {
        lock.lock();
        try {
            Set<Long> keys = cache.keySet();
            for (long key : keys) {
                T obj = cache.get(key);
                releaseForCache(obj);
                references.remove(key);
                cache.remove(key);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * 资源不在缓存时，去磁盘加载资源
     *
     * @param key
     * @return
     */
    protected abstract T getForCache(long key) throws Exception;

    /**
     * 资源移除缓存前，先写回磁盘
     *
     * @param obj
     */
    protected abstract void releaseForCache(T obj);
}
