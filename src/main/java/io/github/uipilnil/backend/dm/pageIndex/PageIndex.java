package io.github.uipilnil.backend.dm.pageIndex;

import io.github.uipilnil.backend.dm.pageCache.PageCache;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 页索引管理器
 */
public class PageIndex {
    // 把一页分成 40 个桶
    private static final int INTERVALS_NO = 40;
    // 计算每个桶的大小
    private static final int THRESHOLD = PageCache.PAGE_SIZE / INTERVALS_NO;

    private Lock lock;
    private List<PageInfo>[] lists;

    @SuppressWarnings("unchecked")
    public PageIndex() {
        lock = new ReentrantLock();
        lists = new List[INTERVALS_NO + 1];
        for (int i = 0; i < INTERVALS_NO + 1; i++) {
            lists[i] = new ArrayList<>();
        }
    }

    /**
     * 根据剩余空间大小，把页加入索引桶
     *
     * @param pgno
     * @param freeSpace
     */
    public void add(int pgno, int freeSpace) {
        lock.lock();
        try {
            // 计算页所在的桶
            int number = freeSpace / THRESHOLD;
            lists[number].add(new PageInfo(pgno, freeSpace));
        } finally {
            lock.unlock();
        }
    }

    /**
     * 根据请求空间大小，选择满足条件的页
     *
     * @param spaceSize
     * @return
     */
    public PageInfo select(int spaceSize) {
        lock.lock();
        try {
            int number = spaceSize / THRESHOLD;
            if (number < INTERVALS_NO) number++;
            while (number <= INTERVALS_NO) {
                if (lists[number].size() == 0) {
                    number++;
                    continue;
                }
                return lists[number].remove(0);
            }
            return null;
        } finally {
            lock.unlock();
        }
    }
}
