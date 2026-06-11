package io.github.uipilnil.backend.dm;

import io.github.uipilnil.backend.common.AbstractCache;
import io.github.uipilnil.backend.dm.dataItem.DataItem;
import io.github.uipilnil.backend.dm.dataItem.DataItemImpl;
import io.github.uipilnil.backend.dm.logger.Logger;
import io.github.uipilnil.backend.dm.page.Page;
import io.github.uipilnil.backend.dm.page.PageOne;
import io.github.uipilnil.backend.dm.page.PageX;
import io.github.uipilnil.backend.dm.pageCache.PageCache;
import io.github.uipilnil.backend.dm.pageIndex.PageIndex;
import io.github.uipilnil.backend.dm.pageIndex.PageInfo;
import io.github.uipilnil.backend.tm.TransactionManager;
import io.github.uipilnil.backend.utils.Panic;
import io.github.uipilnil.backend.utils.Types;
import io.github.uipilnil.common.Error;

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
     * 数据项不在缓存时，去磁盘加载并解析数据项
     *
     * @param uid
     * @return
     */
    @Override
    protected DataItem getForCache(long uid) throws Exception {
        // UID 的结构：[32 位页号] [16 位空位] [16 位偏移]
        // 从 UID 中取出页内偏移
        short offset = (short) (uid & ((1L << 16) - 1));
        uid >>>= 32;
        // 从 UID 中取出页号
        int pgno = (int) (uid & ((1L << 32) - 1));
        Page pg = pc.getPage(pgno);
        return DataItem.parseDataItem(pg, offset, this);
    }

    /**
     * 把数据项移除缓存前，先写回磁盘
     *
     * @param di
     */
    @Override
    protected void releaseForCache(DataItem di) {
        di.page().release();
    }

    /**
     * 读取数据项
     *
     * @param uid
     * @return
     * @throws Exception
     */
    @Override
    public DataItem read(long uid) throws Exception {
        DataItemImpl di = (DataItemImpl) super.get(uid);
        if (!di.isValid()) {
            di.release();
            return null;
        }
        return di;
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
        byte[] raw = DataItem.wrapDataItemRaw(data);
        if (raw.length > PageX.MAX_FREE_SPACE) {
            throw Error.DataTooLargeException;
        }

        // 寻找有足够空闲空间的页，最多尝试五次扩容
        PageInfo pi = null;
        for (int i = 0; i < 5; i++) {
            pi = pIndex.select(raw.length);
            if (pi != null) {
                break;
            } else {
                int newPgno = pc.newPage(PageX.initRaw());
                pIndex.add(newPgno, PageX.MAX_FREE_SPACE);
            }
        }
        if (pi == null) {
            throw Error.DatabaseBusyException;
        }

        Page pg = null;
        int freeSpace = 0;
        try {
            pg = pc.getPage(pi.pgno);
            byte[] log = Recover.insertLog(xid, pg, raw);
            logger.log(log); // 把日志写入缓冲区

            short offset = PageX.insert(pg, raw);

            pg.release();
            return Types.addressToUid(pi.pgno, offset);

        } finally {
            // 把更新后的页重新放回索引
            if (pg != null) {
                pIndex.add(pi.pgno, PageX.getFreeSpace(pg));
            } else {
                pIndex.add(pi.pgno, freeSpace);
            }
        }
    }

    /**
     * 关闭数据管理器，并释放资源
     */
    @Override
    public void close() {
        super.close();
        logger.close();

        PageOne.setVcClose(pageOne);
        pageOne.release();
        pc.close();
    }

    /**
     * 记录更新日志
     *
     * @param xid
     * @param di
     */
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

    /**
     * 创建数据库文件时初始化第一页
     */
    void initPageOne() {
        int pgno = pc.newPage(PageOne.InitRaw());
        assert pgno == 1;
        try {
            pageOne = pc.getPage(pgno);
        } catch (Exception e) {
            Panic.panic(e);
        }
        pc.flushPage(pageOne);
    }

    /**
     * 打开数据库文件时读入第一页，并验证合法性
     *
     * @return
     */
    boolean loadCheckPageOne() {
        try {
            pageOne = pc.getPage(1);
        } catch (Exception e) {
            Panic.panic(e);
        }
        return PageOne.checkVc(pageOne);
    }

    /**
     * 初始化页索引
     */
    void fillPageIndex() {
        int pageNumber = pc.getPageNumber();
        for (int i = 2; i <= pageNumber; i++) {
            Page pg = null;
            try {
                pg = pc.getPage(i);
            } catch (Exception e) {
                Panic.panic(e);
            }
            pIndex.add(pg.getPageNumber(), PageX.getFreeSpace(pg));
            pg.release();
        }
    }
}
