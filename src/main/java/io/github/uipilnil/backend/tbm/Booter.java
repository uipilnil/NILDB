package io.github.uipilnil.backend.tbm;

import io.github.uipilnil.backend.utils.Panic;
import io.github.uipilnil.common.Error;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * 数据库启动引导器
 * 作用：记录首张表的 UID
 * （所有表以链表相连）
 */
public class Booter {
    public static final String BOOTER_SUFFIX = ".bt";           // 正式文件
    public static final String BOOTER_TMP_SUFFIX = ".bt_tmp";   // 临时文件

    String path;    // 数据库基础路径
    File file;      // 数据库文件

    private Booter(String path, File file) {
        this.path = path;
        this.file = file;
    }

    /**
     * 创建启动引导器
     *
     * @param path
     * @return
     */
    public static Booter create(String path) {
        removeBadTmp(path);
        File f = new File(path + BOOTER_SUFFIX);
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
        return new Booter(path, f);
    }

    /**
     * 打开启动引导器
     *
     * @param path
     * @return
     */
    public static Booter open(String path) {
        removeBadTmp(path);
        File f = new File(path + BOOTER_SUFFIX);
        if (!f.exists()) {
            Panic.panic(Error.FileNotExistsException);
        }
        if (!f.canRead() || !f.canWrite()) {
            Panic.panic(Error.FileCannotRWException);
        }
        return new Booter(path, f);
    }

    /**
     * 清理残留的临时文件
     *
     * @param path
     */
    private static void removeBadTmp(String path) {
        new File(path + BOOTER_TMP_SUFFIX).delete();
    }

    /**
     * 读取 .bt 文件中的内容
     *
     * @return
     */
    public byte[] load() {
        byte[] buf = null;
        try {
            buf = Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            Panic.panic(e);
        }
        return buf;
    }

    /**
     * 更新首表 UID
     *
     * @param data 首表 UID
     */
    public void update(byte[] data) {
        File tmp = new File(path + BOOTER_TMP_SUFFIX);
        try {
            tmp.createNewFile();
        } catch (Exception e) {
            Panic.panic(e);
        }
        if (!tmp.canRead() || !tmp.canWrite()) {
            Panic.panic(Error.FileCannotRWException);
        }

        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(data);
            out.flush();
        } catch (IOException e) {
            Panic.panic(e);
        }
        try {
            Files.move(
                    tmp.toPath(),
                    new File(path + BOOTER_SUFFIX).toPath(),
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (IOException e) {
            Panic.panic(e);
        }
        file = new File(path + BOOTER_SUFFIX);
        if (!file.canRead() || !file.canWrite()) {
            Panic.panic(Error.FileCannotRWException);
        }
    }
}
