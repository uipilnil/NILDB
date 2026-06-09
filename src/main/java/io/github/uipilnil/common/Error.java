package io.github.uipilnil.common;

/**
 * 异常公共类
 */
public class Error {
    // common
    public static final Exception FileExistsException = new RuntimeException("File already exists!");
    public static final Exception FileCannotRWException = new RuntimeException("File cannot read or write!");
    public static final Exception FileNotExistsException = new RuntimeException("File does not exists!");
    public static final Exception CacheFullException = new RuntimeException("Cache is full!");

    // dm
    public static final Exception MemTooSmallException = new RuntimeException("Memory too small!");
    public static final Exception BadLogFileException = new RuntimeException("Bad log file!");
    public static final Exception DataTooLargeException = new RuntimeException("Data too large!");

    // tm
    public static final Exception BadXIDFileException = new RuntimeException("Bad XID file!");

    // vm
    public static final Exception DeadlockException = new RuntimeException("Deadlock!");
    public static final Exception NullEntryException = new RuntimeException("Null entry!");
    public static final Exception ConcurrentUpdateException = new RuntimeException("Concurrent update issue!");

    // parser
    public static final Exception InvalidCommandException = new RuntimeException("Invalid command!");
}
