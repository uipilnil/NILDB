package io.github.uipilnil.backend.dm;

import io.github.uipilnil.backend.common.AbstractCache;
import io.github.uipilnil.backend.dm.dataItem.DataItem;
import io.github.uipilnil.backend.dm.logger.Logger;
import io.github.uipilnil.backend.dm.page.Page;
import io.github.uipilnil.backend.dm.pageCache.PageCache;
import io.github.uipilnil.backend.dm.pageIndex.PageIndex;
import io.github.uipilnil.backend.tm.TransactionManager;

/**
 * 数据管理器
 */
public class DataManagerImpl extends AbstractCache<DataItem> implements DataManager {
    TransactionManager tm;
    PageCache pc;
    Logger logger;
    PageIndex pIndex;
    Page pageOne;

    public DataManagerImpl(PageCache pc, Logger logger, TransactionManager tm) {
        super(0);
        this.pc = pc;
        this.logger = logger;
        this.tm = tm;
        this.pIndex = new PageIndex();
    }

    /**
     * 资源不在缓存时，去磁盘加载资源
     *
     * @param uid
     * @return
     */
    @Override
    protected DataItem getForCache(long uid) {
    }

    /**
     * 资源移除缓存前，先写回磁盘
     *
     * @param di
     */
    @Override
    protected void releaseForCache(DataItem di) {
    }

    /**
     * 根据 UID 读数据项
     *
     * @param uid
     * @return
     * @throws Exception
     */
    @Override
    public DataItem read(long uid) throws Exception {
    }

    /**
     * 在指定事务下插入数据
     *
     * @param xid
     * @param data
     * @return
     * @throws Exception
     */
    @Override
    public long insert(long xid, byte[] data) throws Exception {
    }

    /**
     * 关闭数据管理器，并释放资源
     */
    @Override
    public void close() {
    }

    public void logDataItem(long xid, DataItem di) {
        byte[] log = Recover.updateLog(xid, di);
        logger.log(log);
    }

    /**
     * 释放数据项资源
     *
     * @param di
     */
    public void releaseDataItem(DataItem di) {
        super.release(di.getUid());
    }
}
