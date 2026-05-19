package io.github.uipilnil.backend.tm;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import io.github.uipilnil.backend.utils.Panic;
import io.github.uipilnil.common.Error;

public interface TransactionManager {
    long begin();
    void commit(long xid);
    void abort(long xid);
    boolean isActive(long xid);
    boolean isCommitted(long xid);
    boolean isAborted(long xid);
    void close();

    /**
     * 创建一个事务管理器，并返回这个管理器对象
     * @param path XID 文件的基础路径
     * @return
     */
    public static TransactionManagerImpl create(String path) {
        File f = new File(path + TransactionManagerImpl.XID_SUFFIX);
        try {
            if (!f.createNewFile()) {
                Panic.panic(Error.FileExistsException);
            }
        } catch (Exception e) {
            Panic.panic(e);
        }
        if (!f.canRead() || !f.canWrite()) {
            Panic.panic(Error.FileCannotRWException);
        }

        // 两个操作文件的工具对象
        RandomAccessFile raf = null;
        FileChannel fc = null;
        try {
            // 打开文件，拿到读写权限
            raf = new RandomAccessFile(f, "rw");
            // 从 raf 里取出一个文件通道，支持随时修改文件任意位置。后续用它记录事务状态
            fc = raf.getChannel();
        } catch (FileNotFoundException e) {
            Panic.panic(e);
        }

        // 给 XID 文件写一个空文件头
        // 搞一个容器，装字节数据
        ByteBuffer buf = ByteBuffer.wrap(new byte[TransactionManagerImpl.LEN_XID_HEADER_LENGTH]);
        try {
            fc.position(0);
            fc.write(buf);
        } catch (IOException e) {
            Panic.panic(e);
        }

        return new TransactionManagerImpl(raf, fc);
    }

    /**
     * 打开一个已经创建好的事务管理器
     * @param path XID 文件的基础路径
     * @return
     */
    public static TransactionManagerImpl open(String path) {
        File f = new File(path + TransactionManagerImpl.XID_SUFFIX);
        if (!f.exists()) {
            Panic.panic(Error.FileNotExistsException);
        }
        if (!f.canRead() || !f.canWrite()) {
            Panic.panic(Error.FileCannotRWException);
        }

        // 两个操作文件的工具对象
        RandomAccessFile raf = null;
        FileChannel fc = null;
        try {
            // 打开文件，拿到读写权限
            raf = new RandomAccessFile(f, "rw");
            // 从 raf 里取出一个文件通道，支持随时修改文件任意位置。后续用它记录事务状态
            fc = raf.getChannel();
        } catch (FileNotFoundException e) {
            Panic.panic(e);
        }

        return new TransactionManagerImpl(raf, fc);
    }
}
