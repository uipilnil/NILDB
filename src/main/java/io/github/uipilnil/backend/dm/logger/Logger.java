package io.github.uipilnil.backend.dm.logger;

import io.github.uipilnil.backend.utils.Panic;
import io.github.uipilnil.backend.utils.Parser;
import io.github.uipilnil.common.Error;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public interface Logger {
    void log(byte[] data);

    byte[] next();

    void rewind();

    void truncate(long x) throws Exception;

    void close();

    /**
     * 新建日志文件
     *
     * @param path
     * @return
     */
    public static Logger create(String path) {
        File f = new File(path + LoggerImpl.LOG_SUFFIX);
        try {
            if (!f.createNewFile()) {
                Panic.panic(Error.FileExistsException);
            }
        } catch (Exception e) {
            Panic.panic(e);
        }
        if (!f.canRead() || !f.canWrite()) {
            Panic.panic(Error.FileExistsException);
        }

        RandomAccessFile raf = null;
        FileChannel fc = null;
        try {
            // 打开文件，拿到读写权限
            raf = new RandomAccessFile(f, "rw");
            // 从 raf 里取出一个文件通道，支持随时修改文件任意位置
            fc = raf.getChannel();
        } catch (FileNotFoundException e) {
            Panic.panic(e);
        }

        ByteBuffer buf = ByteBuffer.wrap(Parser.intToByte(0));
        try {
            fc.position(0);
            fc.write(buf);
            fc.force(false);
        } catch (IOException e) {
            Panic.panic(e);
        }

        return new LoggerImpl(raf, fc, 0);
    }

    /**
     * 打开日志文件
     *
     * @param path
     * @return
     */
    public static Logger open(String path) {
        File f = new File(path + LoggerImpl.LOG_SUFFIX);
        if (!f.exists()) {
            Panic.panic(Error.FileNotExistsException);
        }
        if (!f.canRead() || !f.canWrite()) {
            Panic.panic(Error.FileCannotRWException);
        }

        RandomAccessFile raf = null;
        FileChannel fc = null;
        try {
            raf = new RandomAccessFile(f, "rw");
            fc = raf.getChannel();
        } catch (FileNotFoundException e) {
            Panic.panic(e);
        }

        LoggerImpl lg = new LoggerImpl(raf, fc);
        lg.init();

        return lg;
    }
}
