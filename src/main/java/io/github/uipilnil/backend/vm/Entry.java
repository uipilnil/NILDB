package io.github.uipilnil.backend.vm;

import com.google.common.primitives.Bytes;
import io.github.uipilnil.backend.common.SubArray;
import io.github.uipilnil.backend.dm.dataItem.DataItem;
import io.github.uipilnil.backend.utils.Parser;

import java.util.Arrays;

/**
 * 数据封装器
 * entry 的结构：[XMIN] [XMAX] [data]
 */
public class Entry {
    private static final int OF_XMIN = 0;           // 创建数据的事务 ID
    private static final int OF_XMAX = OF_XMIN + 8;   // 删除数据的事务 ID
    private static final int OF_DATA = OF_XMAX + 8;   // 用户数据起始偏移

    private long uid;
    private DataItem dataItem;  // DM 提供数据
    private VersionManager vm;  // VM 提供数据操作入口

    /**
     * 创建记录对象，封装底层数据项
     *
     * @param vm
     * @param dataItem
     * @param uid
     * @return
     */
    public static Entry newEntry(VersionManager vm, DataItem dataItem, long uid) {
        if (dataItem == null) {
            return null;
        }
        Entry entry = new Entry();
        entry.uid = uid;
        entry.dataItem = dataItem;
        entry.vm = vm;
        return entry;
    }

    /**
     * 从磁盘加载数据项，并把它封装成记录
     *
     * @param vm
     * @param uid
     * @return
     * @throws Exception
     */
    public static Entry loadEntry(VersionManager vm, long uid) throws Exception {
        DataItem di = ((VersionManagerImpl) vm).dm.read(uid);
        return newEntry(vm, di, uid);
    }

    /**
     * 把数据包装成记录格式
     *
     * @param xid
     * @param data
     * @return
     */
    public static byte[] wrapEntryRaw(long xid, byte[] data) {
        byte[] xmin = Parser.longToByte(xid);
        byte[] xmax = new byte[8];
        return Bytes.concat(xmin, xmax, data);
    }

    /**
     * 释放记录的引用
     */
    public void release() {
        ((VersionManagerImpl) vm).releaseEntry(this);
    }

    /**
     * 删除底层数据项
     */
    public void remove() {
        dataItem.release();
    }

    /**
     * 以拷贝的形式返回数据
     *
     * @return
     */
    public byte[] data() {
        dataItem.rLock();
        try {
            SubArray sa = dataItem.data();
            byte[] data = new byte[sa.end - sa.start - OF_DATA];
            System.arraycopy(sa.raw, sa.start + OF_DATA, data, 0, data.length);
            return data;
        } finally {
            dataItem.rUnLock();
        }
    }

    /**
     * 获取创建数据的事务 ID
     *
     * @return
     */
    public long getXmin() {
        dataItem.rLock();
        try {
            SubArray sa = dataItem.data();
            return Parser.parseLong(Arrays.copyOfRange(sa.raw, sa.start + OF_XMIN, sa.start + OF_XMAX));
        } finally {
            dataItem.rUnLock();
        }
    }

    /**
     * 获取删除数据的事务 ID
     *
     * @return
     */
    public long getXmax() {
        dataItem.rLock();
        try {
            SubArray sa = dataItem.data();
            return Parser.parseLong(Arrays.copyOfRange(sa.raw, sa.start + OF_XMAX, sa.start + OF_DATA));
        } finally {
            dataItem.rUnLock();
        }
    }

    /**
     * 标记数据被删除
     *
     * @param xid
     */
    public void setXmax(long xid) {
        dataItem.before();
        try {
            SubArray sa = dataItem.data();
            System.arraycopy(Parser.longToByte(xid), 0, sa.raw, sa.start + OF_XMAX, 8);
        } finally {
            dataItem.after(xid);
        }
    }

    /**
     * 获取数据 ID
     *
     * @return
     */
    public long getUid() {
        return uid;
    }
}
