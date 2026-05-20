package io.github.uipilnil.backend.tm;

import io.github.uipilnil.backend.utils.Panic;
import io.github.uipilnil.backend.utils.Parser;
import io.github.uipilnil.common.Error;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 事务管理器
 */
public class TransactionManagerImpl implements TransactionManager {
    // XID 文件头长度
    static final int LEN_XID_HEADER_LENGTH = 8;
    // 每个事务占用的长度
    private static final int XID_FIELD_SIZE = 1;

    // 事务的三种状态
    private static final byte FIELD_TRAN_ACTIVE = 0;
    private static final byte FIELD_TRAN_COMMITTED = 1;
    private static final byte FIELD_TRAN_ABORTED = 2;

    // 超级事务，永远处于 commited 状态
    public static final long SUPER_XID = 0;

    static final String XID_SUFFIX = ".xid";

    private RandomAccessFile file;
    private FileChannel fc;
    // 最大事务 ID
    private long xidCounter;
    // 并发锁
    private Lock counterLock;

    TransactionManagerImpl(RandomAccessFile raf, FileChannel fc) {
        this.file = raf;
        this.fc = fc;
        counterLock = new ReentrantLock();
        // 在 open() 时检查 XID 文件是否合法
        checkXIDCounter();
    }

    /**
     * 检查 XID 文件是否合法
     */
    private void checkXIDCounter() {
        long fileLen = 0;
        try {
            fileLen = file.length();
        } catch (IOException e1) {
            Panic.panic(Error.BadXIDFileException);
        }
        // 文件比文件头短：说明文件损坏
        if (fileLen < LEN_XID_HEADER_LENGTH) {
            Panic.panic(Error.BadXIDFileException);
        }

        // 读出文件头的 8 字节
        ByteBuffer buf = ByteBuffer.allocate(LEN_XID_HEADER_LENGTH);
        try {
            fc.position(0);
            fc.read(buf);
        } catch (IOException e) {
            Panic.panic(e);
        }
        this.xidCounter = Parser.parseLong(buf.array());
        // 计算文件的理论长度，再和实际长度比较，不一样说明文件损坏
        long end = getXidPosition(this.xidCounter + 1);
        if (end != fileLen) {
            Panic.panic(Error.BadXIDFileException);
        }
    }

    /**
     * 根据事务的 XID 计算出它在 XID 文件中的位置
     *
     * @param xid 事务
     * @return 事务在 XID 文件中的位置
     */
    private long getXidPosition(long xid) {
        return LEN_XID_HEADER_LENGTH + (xid - 1) * XID_FIELD_SIZE;
    }

    /**
     * 把事务 XID 的状态更新为 status
     *
     * @param xid    事务
     * @param status 事务的新状态
     */
    private void updateXID(long xid, byte status) {
        long offset = getXidPosition(xid);
        byte[] tmp = new byte[XID_FIELD_SIZE];
        tmp[0] = status;
        ByteBuffer buf = ByteBuffer.wrap(tmp);
        try {
            fc.position(offset);
            fc.write(buf);
        } catch (IOException e) {
            Panic.panic(e);
        }
        // 把缓冲区数据强制写入硬盘
        try {
            fc.force(false);
        } catch (IOException e) {
            Panic.panic(e);
        }
    }

    /**
     * 把最大事务 ID 加一，并更新 XID 文件头
     */
    private void incrXIDCounter() {
        xidCounter++;
        ByteBuffer buf = ByteBuffer.wrap(Parser.longToByte(xidCounter));
        try {
            fc.position(0);
            fc.write(buf);
        } catch (IOException e) {
            Panic.panic(e);
        }
        // 把缓冲区数据强制写入硬盘
        try {
            fc.force(false);
        } catch (IOException e) {
            Panic.panic(e);
        }
    }

    /**
     * 开启事务
     *
     * @return 事务编号 XID
     */
    @Override
    public long begin() {
        // 防止多线程下同时开启事务
        counterLock.lock();
        try {
            long xid = xidCounter + 1;
            updateXID(xid, FIELD_TRAN_ACTIVE);
            incrXIDCounter();
            return xid;
        } finally {
            counterLock.unlock();
        }
    }

    /**
     * 提交事务
     *
     * @param xid
     */
    @Override
    public void commit(long xid) {
        updateXID(xid, FIELD_TRAN_COMMITTED);
    }

    /**
     * 回滚事务
     *
     * @param xid
     */
    @Override
    public void abort(long xid) {
        updateXID(xid, FIELD_TRAN_ABORTED);
    }

    /**
     * 判断事务是否为指定状态
     *
     * @param xid
     * @param status
     * @return
     */
    private boolean checkXID(long xid, byte status) {
        long offset = getXidPosition(xid);
        ByteBuffer buf = ByteBuffer.wrap(new byte[XID_FIELD_SIZE]);
        try {
            fc.position(offset);
            fc.read(buf);
        } catch (IOException e) {
            Panic.panic(e);
        }

        return buf.array()[0] == status;
    }

    /**
     * 判断事务是否活跃
     *
     * @param xid
     * @return
     */
    @Override
    public boolean isActive(long xid) {
        if (xid == SUPER_XID) return false;
        return checkXID(xid, FIELD_TRAN_ACTIVE);
    }

    /**
     * 判断事务是否已提交
     *
     * @param xid
     * @return
     */
    @Override
    public boolean isCommitted(long xid) {
        if (xid == SUPER_XID) return true;
        return checkXID(xid, FIELD_TRAN_COMMITTED);
    }

    /**
     * 判断事务是否已回滚作废
     *
     * @param xid
     * @return
     */
    @Override
    public boolean isAborted(long xid) {
        if (xid == SUPER_XID) return false;
        return checkXID(xid, FIELD_TRAN_ABORTED);
    }

    /**
     * 关闭事务管理器
     */
    @Override
    public void close() {
        try {
            fc.close();
            file.close();
        } catch (IOException e) {
            Panic.panic(e);
        }
    }
}
