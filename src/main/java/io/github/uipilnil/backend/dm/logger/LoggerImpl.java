package io.github.uipilnil.backend.dm.logger;

import com.google.common.primitives.Bytes;
import io.github.uipilnil.backend.utils.Panic;
import io.github.uipilnil.backend.utils.Parser;
import io.github.uipilnil.common.Error;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 日志文件管理器
 * 日志文件格式：[XChecksum] [Log1] [Log2] ... [LogN] [BadTail]
 * XChecksum：日志文件的校验码，是 int 类型
 * 日志格式：[Size] [Checksum] [Data]
 * Size：标识数据长度，4 字节，int 类型
 * Checksum：单条日志的校验码，4 字节，int 类型
 */
public class LoggerImpl implements Logger {
    // 用于多项式滚动哈希的常数种子
    private static final int SEED = 13331;

    // 单条日志内部字段的相对偏移量
    private static final int OF_SIZE = 0; // Size：0
    private static final int OF_CHECKSUM = OF_SIZE + 4; // Checksum：4
    private static final int OF_DATA = OF_CHECKSUM + 4; // Data：8

    public static final String LOG_SUFFIX = ".log";

    private RandomAccessFile file;
    private FileChannel fc;
    private Lock lock;

    private long position;
    private long fileSize;
    private int xChecksum; // 日志文件的校验码

    LoggerImpl(RandomAccessFile raf, FileChannel fc) {
        this.file = raf;
        this.fc = fc;
        lock = new ReentrantLock();
    }

    LoggerImpl(RandomAccessFile raf, FileChannel fc, int xChecksum) {
        this.file = raf;
        this.fc = fc;
        this.xChecksum = xChecksum;
        lock = new ReentrantLock();
    }

    /**
     * 初始化日志文件
     */
    void init() {
        long size = 0;
        try {
            size = file.length();
        } catch (IOException e) {
            Panic.panic(e);
        }

        // 日志文件至少包含头部 4 字节 xChecksum
        if (size < 4) {
            Panic.panic(Error.BadLogFileException);
        }

        // 4 字节缓冲区读取文件头部 xChecksum
        ByteBuffer raw = ByteBuffer.allocate(4);
        try {
            fc.position(0);
            fc.read(raw);
        } catch (IOException e) {
            Panic.panic(e);
        }
        this.xChecksum = Parser.parseInt(raw.array());
        this.fileSize = size;

        checkAndRemoveTail();
    }

    /**
     * 检查并移除日志文件的坏尾
     */
    private void checkAndRemoveTail() {
        rewind();

        // 计算实际的校验码
        int xCheck = 0;
        while (true) {
            byte[] log = internNext();
            if (log == null) break;
            xCheck = calChecksum(xCheck, log);
        }

        if (xCheck != xChecksum) {
            Panic.panic(Error.BadLogFileException);
        }

        // 把文件截断到有效日志末尾，移除坏尾
        try {
            truncate(position);
        } catch (Exception e) {
            Panic.panic(e);
        }

        // 把文件指针定位到截断后的位置
        try {
            file.seek(position);
        } catch (IOException e) {
            Panic.panic(e);
        }

        rewind();
    }

    /**
     * 读取下一条日志
     *
     * @return
     */
    private byte[] internNext() {
        // 判断剩余位置还能不能盛下一条日志
        if (position + OF_DATA >= fileSize) {
            return null;
        }

        // 读取 Size 字段
        ByteBuffer tmp = ByteBuffer.allocate(4);
        try {
            fc.position(position);
            fc.read(tmp);
        } catch (IOException e) {
            Panic.panic(e);
        }
        int size = Parser.parseInt(tmp.array());

        // 判断日志的完整性
        if (position + OF_DATA + size > fileSize) {
            return null;
        }

        // 读日志
        ByteBuffer buf = ByteBuffer.allocate(OF_DATA + size);
        try {
            fc.position(position);
            fc.read(buf);
        } catch (IOException e) {
            Panic.panic(e);
        }

        byte[] log = buf.array();
        // 实际的单日志校验码
        int checkSum1 = calChecksum(0, Arrays.copyOfRange(log, OF_DATA, log.length));
        // 理论的单日志校验码
        int checkSum2 = Parser.parseInt(Arrays.copyOfRange(log, OF_CHECKSUM, OF_DATA));
        // 判断校验码是否正确
        if (checkSum1 != checkSum2) {
            return null;
        }

        position += log.length;

        return log;
    }

    /**
     * 计算单日志校验码
     *
     * @param xCheck 上一轮校验码
     * @param log
     * @return
     */
    private int calChecksum(int xCheck, byte[] log) {
        for (byte b : log) {
            xCheck = xCheck * SEED + b;
        }
        return xCheck;
    }

    /**
     * 把日志写入日志文件
     *
     * @param data
     */
    @Override
    public void log(byte[] data) {
        byte[] log = wrapLog(data);
        ByteBuffer buf = ByteBuffer.wrap(log);
        lock.lock();
        try {
            fc.position(fc.size());
            fc.write(buf);
        } catch (IOException e) {
            Panic.panic(e);
        } finally {
            lock.unlock();
        }

        updateXChecksum(log);
    }

    /**
     * 把数据包装成日志格式
     *
     * @param data
     * @return
     */
    private byte[] wrapLog(byte[] data) {
        byte[] checksum = Parser.int2Byte(calChecksum(0, data));
        byte[] size = Parser.int2Byte(data.length);

        return Bytes.concat(size, checksum, data);
    }

    /**
     * 更新日志文件的校验码
     *
     * @param log
     */
    private void updateXChecksum(byte[] log) {
        this.xChecksum = calChecksum(this.xChecksum, log);

        try {
            fc.position(0);
            fc.write(ByteBuffer.wrap(Parser.int2Byte(xChecksum)));
            fc.force(false);
        } catch (IOException e) {
            Panic.panic(e);
        }
    }

    /**
     * 获取下一条日志的纯数据 Data
     *
     * @return
     */
    @Override
    public byte[] next() {
        lock.lock();
        try {
            byte[] log = internNext();
            if (log == null) return null;
            return Arrays.copyOfRange(log, OF_DATA, log.length);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 重置日志指针，置于第一条日志开始位置
     */
    @Override
    public void rewind() {
        position = 4;
    }

    /**
     * 截断文件到指定长度
     *
     * @param x 截断位置
     * @throws Exception
     */
    @Override
    public void truncate(long x) throws Exception {
        lock.lock();
        try {
            fc.truncate(x);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 关闭日志文件的相关资源
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
