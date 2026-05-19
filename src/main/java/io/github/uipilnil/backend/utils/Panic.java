package io.github.uipilnil.backend.utils;

public class Panic {
    /**
     * 数据库中遇到无法恢复的错误，直接崩溃
     * @param err
     */
    public static void panic(Exception err) {
        err.printStackTrace();
        System.exit(1); // 1 非正常退出；0 正常退出
    }
}
