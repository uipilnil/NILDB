package io.github.uipilnil.backend.dm;

import com.google.common.primitives.Bytes;
import io.github.uipilnil.backend.common.SubArray;
import io.github.uipilnil.backend.dm.dataItem.DataItem;
import io.github.uipilnil.backend.dm.logger.Logger;
import io.github.uipilnil.backend.dm.page.Page;
import io.github.uipilnil.backend.dm.page.PageX;
import io.github.uipilnil.backend.dm.pageCache.PageCache;
import io.github.uipilnil.backend.tm.TransactionManager;
import io.github.uipilnil.backend.utils.Panic;
import io.github.uipilnil.backend.utils.Parser;

import java.util.*;

/**
 * 数据库崩溃恢复
 */
public class Recover {
    private static final byte LOG_TYPE_INSERT = 0; // 定义插入类型的日志
    private static final byte LOG_TYPE_UPDATE = 1; // 定义更新类型的日志

    private static final int REDO = 0; // 定义重做操作
    private static final int UNDO = 1; // 定义撤销操作

    // 更新日志的结构：[LogType] [XID] [UID] [OldRaw] [NewRaw]
    private static final int OF_TYPE = 0;
    private static final int OF_XID = OF_TYPE + 1;
    private static final int OF_UPDATE_UID = OF_XID + 8;
    private static final int OF_UPDATE_RAW = OF_UPDATE_UID + 8;

    // 日志的结构：[LogType] [XID] [Pgno] [Offset] [Raw]
    private static final int OF_INSERT_PGNO = OF_XID + 8;
    private static final int OF_INSERT_OFFSET = OF_INSERT_PGNO + 4;
    private static final int OF_INSERT_RAW = OF_INSERT_OFFSET + 2;

    /**
     * 存放插入日志信息
     */
    static class InsertLogInfo {
        long xid;
        int pgno;
        short offset;
        byte[] raw;
    }

    /**
     * 存放更新日志信息
     */
    static class UpdateLogInfo {
        long xid;
        int pgno;
        short offset;
        byte[] oldRaw;
        byte[] newRaw;
    }

    /**
     * 数据库崩溃恢复
     *
     * @param tm
     * @param lg
     * @param pc
     */
    public static void recover(TransactionManager tm, Logger lg, PageCache pc) {
        System.out.println("Recovering...");

        lg.rewind();

        int maxPgno = 0;

        // 读所有日志
        while (true) {
            byte[] log = lg.next();
            if (log == null) break;
            int pgno;
            if (isInsertLog(log)) {
                InsertLogInfo li = parseInsertLog(log);
                pgno = li.pgno;
            } else {
                UpdateLogInfo li = parseUpdateLog(log);
                pgno = li.pgno;
            }
            if (pgno > maxPgno) {
                maxPgno = pgno;
            }
        }

        // 即使没有日志，也至少有系统页作为第一页
        if (maxPgno == 0) {
            maxPgno = 1;
        }

        // 截断日志，去掉脏尾
        pc.truncateByBgno(maxPgno);
        System.out.println("Truncate to " + maxPgno + " pages.");

        // 重做已提交的事务
        redoTranscations(tm, lg, pc);
        System.out.println("Redo Transactions Over.");

        // 回滚未提交的事务
        undoTranscations(tm, lg, pc);
        System.out.println("Undo Transactions Over.");

        System.out.println("Recovery Over.");
    }

    /**
     * 重做已提交的事务
     *
     * @param tm
     * @param lg
     * @param pc
     */
    private static void redoTranscations(TransactionManager tm, Logger lg, PageCache pc) {
        lg.rewind();
        while (true) {
            byte[] log = lg.next();
            if (log == null) break;
            if (isInsertLog(log)) {
                InsertLogInfo li = parseInsertLog(log);
                long xid = li.xid;
                // 重做不活跃的事务
                if (!tm.isActive(xid)) {
                    doInsertLog(pc, log, REDO);
                }
            } else {
                UpdateLogInfo xi = parseUpdateLog(log);
                long xid = xi.xid;
                // 重做不活跃的事务
                if (!tm.isActive(xid)) {
                    doUpdateLog(pc, log, REDO);
                }
            }
        }
    }

    /**
     * 回滚未提交的事务
     *
     * @param tm
     * @param lg
     * @param pc
     */
    private static void undoTranscations(TransactionManager tm, Logger lg, PageCache pc) {
        Map<Long, List<byte[]>> logCache = new HashMap<>();
        lg.rewind();
        while (true) {
            byte[] log = lg.next();
            if (log == null) break;
            if (isInsertLog(log)) {
                InsertLogInfo li = parseInsertLog(log);
                long xid = li.xid;
                // 回滚活跃的事务
                if (tm.isActive(xid)) {
                    // 对于第一次出现的事务，在缓存中给它新建日志列表
                    if (!logCache.containsKey(xid)) {
                        logCache.put(xid, new ArrayList<>());
                    }
                    logCache.get(xid).add(log);
                }
            } else {
                UpdateLogInfo xi = parseUpdateLog(log);
                long xid = xi.xid;
                // 回滚活跃的事务
                if (tm.isActive(xid)) {
                    // 对于第一次出现的事务，在缓存中给它新建日志列表
                    if (!logCache.containsKey(xid)) {
                        logCache.put(xid, new ArrayList<>());
                    }
                    logCache.get(xid).add(log);
                }
            }
        }

        // 对于所有活跃事务，要倒序回滚
        // <事务 ID, 事务的所有操作日志>
        for (Map.Entry<Long, List<byte[]>> entry : logCache.entrySet()) {
            List<byte[]> logs = entry.getValue();
            for (int i = logs.size() - 1; i >= 0; i--) {
                byte[] log = logs.get(i);
                if (isInsertLog(log)) {
                    doInsertLog(pc, log, UNDO);
                } else {
                    doUpdateLog(pc, log, UNDO);
                }
            }
            tm.abort(entry.getKey());
        }
    }

    /**
     * 判断插入日志
     *
     * @param log
     * @return
     */
    private static boolean isInsertLog(byte[] log) {
        return log[0] == LOG_TYPE_INSERT;
    }

    /**
     * 生成插入日志，把信息打包成字节数组
     *
     * @param xid
     * @param pg
     * @param raw
     * @return
     */
    public static byte[] insertLog(long xid, Page pg, byte[] raw) {
        byte[] logTypeRaw = {LOG_TYPE_INSERT};
        byte[] xidRaw = Parser.longToByte(xid);
        byte[] pgnoRaw = Parser.intToByte(pg.getPageNumber());
        byte[] offsetRaw = Parser.shortToByte(PageX.getFSO(pg));
        return Bytes.concat(logTypeRaw, xidRaw, pgnoRaw, offsetRaw, raw);
    }

    /**
     * 把插入日志从二进制数据解析成结构化对象
     *
     * @param log
     * @return
     */
    private static InsertLogInfo parseInsertLog(byte[] log) {
        InsertLogInfo li = new InsertLogInfo();
        li.xid = Parser.parseLong(Arrays.copyOfRange(log, OF_XID, OF_INSERT_PGNO));
        li.pgno = Parser.parseInt(Arrays.copyOfRange(log, OF_INSERT_PGNO, OF_INSERT_OFFSET));
        li.offset = Parser.parseShort(Arrays.copyOfRange(log, OF_INSERT_OFFSET, OF_INSERT_RAW));
        li.raw = Arrays.copyOfRange(log, OF_INSERT_RAW, log.length);
        return li;
    }

    /**
     * 执行插入日志
     *
     * @param pc
     * @param log
     * @param flag
     */
    private static void doInsertLog(PageCache pc, byte[] log, int flag) {
        InsertLogInfo li = parseInsertLog(log);

        Page pg = null;
        try {
            pg = pc.getPage(li.pgno);
        } catch (Exception e) {
            Panic.panic(e);
        }

        try {
            // 对于回滚操作，把数据项标记为无效
            if (flag == UNDO) {
                DataItem.setDataItemRawInvalid(li.raw);
            }
            PageX.recoverInsert(pg, li.raw, li.offset);
        } finally {
            pg.release();
        }
    }

    /**
     * 生成更新日志，把信息打包成字节数组
     *
     * @param xid
     * @param di
     * @return
     */
    public static byte[] updateLog(long xid, DataItem di) {
        byte[] logType = {LOG_TYPE_UPDATE};
        byte[] xidRaw = Parser.longToByte(xid);
        byte[] uidRaw = Parser.longToByte(di.getUid());
        byte[] oldRaw = di.getOldRaw();
        SubArray raw = di.getRaw();
        byte[] newRaw = Arrays.copyOfRange(raw.raw, raw.start, raw.end);
        return Bytes.concat(logType, xidRaw, uidRaw, oldRaw, newRaw);
    }

    /**
     * 把更新日志从二进制数据解析成结构化对象
     *
     * @param log
     * @return
     */
    private static UpdateLogInfo parseUpdateLog(byte[] log) {
        UpdateLogInfo li = new UpdateLogInfo();
        li.xid = Parser.parseLong(Arrays.copyOfRange(log, OF_XID, OF_UPDATE_UID));
        long uid = Parser.parseLong(Arrays.copyOfRange(log, OF_UPDATE_UID, OF_UPDATE_RAW));
        li.offset = (short) (uid & ((1L << 16) - 1));
        uid >>>= 32;
        li.pgno = (int) (uid & ((1L << 32) - 1));
        int length = (log.length - OF_UPDATE_RAW) / 2;
        li.oldRaw = Arrays.copyOfRange(log, OF_UPDATE_RAW, OF_UPDATE_RAW + length);
        li.newRaw = Arrays.copyOfRange(log, OF_UPDATE_RAW + length, OF_UPDATE_RAW + length * 2);
        return li;
    }

    /**
     * 执行更新日志
     *
     * @param pc
     * @param log
     * @param flag
     */
    private static void doUpdateLog(PageCache pc, byte[] log, int flag) {
        int pgno;
        short offset;
        byte[] raw;

        // 判断重做/回滚，解析日志
        if (flag == REDO) {
            UpdateLogInfo xi = parseUpdateLog(log);
            pgno = xi.pgno;
            offset = xi.offset;
            raw = xi.newRaw;
        } else {
            UpdateLogInfo xi = parseUpdateLog(log);
            pgno = xi.pgno;
            offset = xi.offset;
            raw = xi.oldRaw;
        }

        Page pg = null;
        try {
            pg = pc.getPage(pgno);
        } catch (Exception e) {
            Panic.panic(e);
        }

        try {
            PageX.recoverUpdate(pg, raw, offset);
        } finally {
            pg.release();
        }
    }
}