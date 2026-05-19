package io.github.uipilnil.backend.tm;

import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;

public class TransactionManagerImpl {
    static final String XID_SUFFIX = ".xid";
    // XID 文件头长度
    static final int LEN_XID_HEADER_LENGTH = 8;

    private RandomAccessFile file;
    private FileChannel fc;

    TransactionManagerImpl(RandomAccessFile raf, FileChannel fc) {
        this.file = raf;
        this.fc = fc;
    }
}
