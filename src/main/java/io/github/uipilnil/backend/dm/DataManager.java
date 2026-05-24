package io.github.uipilnil.backend.dm;

import io.github.uipilnil.backend.dm.dataItem.DataItem;
import io.github.uipilnil.backend.dm.logger.Logger;
import io.github.uipilnil.backend.dm.page.PageOne;
import io.github.uipilnil.backend.dm.pageCache.PageCache;
import io.github.uipilnil.backend.tm.TransactionManager;

public interface DataManager {
    DataItem read(long uid) throws Exception;

    long insert(long xid, byte[] data) throws Exception;

    void close();

    /**
     * 创建一个数据管理器，并返回这个管理器对象
     *
     * @param path
     * @param mem
     * @param tm
     * @return
     */
    public static DataManager create(String path, long mem, TransactionManager tm) {
        PageCache pc = PageCache.create(path, mem);
        Logger lg = Logger.create(path);

        DataManagerImpl dm = new DataManagerImpl(pc, lg, tm);
        dm.initPageOne();

        return dm;
    }

    /**
     * 打开一个已经创建好的数据管理器
     *
     * @param path
     * @param mem
     * @param tm
     * @return
     */
    public static DataManager open(String path, long mem, TransactionManager tm) {
        PageCache pc = PageCache.open(path, mem);
        Logger lg = Logger.open(path);
        DataManagerImpl dm = new DataManagerImpl(pc, lg, tm);
        if (!dm.loadCheckPageOne()) {
            Recover.recover(tm, lg, pc);
        }
        dm.fillPageIndex();

        // 把数据库状态更新为已打开，并把状态刷盘
        PageOne.setVcOpen(dm.pageOne);
        dm.pc.flushPage(dm.pageOne);

        return dm;
    }
}
