package io.github.uipilnil.backend;

import io.github.uipilnil.backend.dm.DataManager;
import io.github.uipilnil.backend.server.Server;
import io.github.uipilnil.backend.tbm.TableManager;
import io.github.uipilnil.backend.tm.TransactionManager;
import io.github.uipilnil.backend.utils.Panic;
import io.github.uipilnil.backend.vm.VersionManager;
import io.github.uipilnil.backend.vm.VersionManagerImpl;
import io.github.uipilnil.common.Error;
import org.apache.commons.cli.*;

/**
 * 服务器启动器
 */
public class Launcher {
    public static final int port = 1201;

    public static final long DEFALUT_MEM = (1 << 20) * 64;
    public static final long KB = 1 << 10;
    public static final long MB = 1 << 20;
    public static final long GB = 1 << 30;

    public static void main(String[] args) throws ParseException {
        Options options = new Options();
        options.addOption("open", true, "-open DBPath");
        options.addOption("create", true, "-create DBPath");
        options.addOption("mem", true, "-mem 64MB");
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);

        if (cmd.hasOption("open")) {
            openDB(cmd.getOptionValue("open"), parseMem(cmd.getOptionValue("mem")));
            return;
        }
        if (cmd.hasOption("create")) {
            createDB(cmd.getOptionValue("create"));
            return;
        }
        System.out.println("Usage: launcher (open|create) DBPath");
    }

    /**
     * 创建数据库
     *
     * @param path
     */
    private static void createDB(String path) {
        TransactionManager tm = TransactionManager.create(path);
        DataManager dm = DataManager.create(path, DEFALUT_MEM, tm);
        VersionManager vm = new VersionManagerImpl(tm, dm);
        TableManager.create(path, vm, dm);
        tm.close();
        dm.close();
    }

    /**
     * 启动数据库
     *
     * @param path
     * @param mem
     */
    private static void openDB(String path, long mem) {
        TransactionManager tm = TransactionManager.open(path);
        DataManager dm = DataManager.open(path, mem, tm);
        VersionManager vm = new VersionManagerImpl(tm, dm);
        TableManager tbm = TableManager.open(path, vm, dm);
        new Server(port, tbm).start();
    }

    /**
     * 内存参数解析工具
     *
     * @param memStr
     * @return
     */
    private static long parseMem(String memStr) {
        if (memStr == null || "".equals(memStr)) {
            return DEFALUT_MEM;
        }
        if (memStr.length() < 2) {
            Panic.panic(Error.InvalidMemException);
        }
        String unit = memStr.substring(memStr.length() - 2);
        long memNum = Long.parseLong(memStr.substring(0, memStr.length() - 2));
        switch (unit) {
            case "KB":
                return memNum * KB;
            case "MB":
                return memNum * MB;
            case "GB":
                return memNum * GB;
            default:
                Panic.panic(Error.InvalidMemException);
        }
        return DEFALUT_MEM;
    }
}
