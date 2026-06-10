package io.github.uipilnil.backend.utils;

/**
 * 字符串解析后返回值封装类
 */
public class ParseStringRes {
    public String str;
    public int next;    // 下次读取的起始偏移

    public ParseStringRes(String str, int next) {
        this.str = str;
        this.next = next;
    }
}
