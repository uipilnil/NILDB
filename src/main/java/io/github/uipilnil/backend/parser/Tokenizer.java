package io.github.uipilnil.backend.parser;

import io.github.uipilnil.common.Error;

/**
 * 词法分析器
 */
public class Tokenizer {
    private byte[] stat;            // 整条 SQL
    private int pos;                // 解析指针，记录当前解析位置
    private String currentToken;    // 缓存当前 token，下次访问时直接返回
    private boolean flushToken;     // 标记是否需要解析下一个 token
    private Exception err;

    public Tokenizer(byte[] stat) {
        this.stat = stat;
        this.pos = 0;
        this.currentToken = "";
        this.flushToken = true;
    }

    /**
     * 访问下一个 token
     *
     * @return
     * @throws Exception
     */
    public String peek() throws Exception {
        if (err != null) {
            throw err;
        }

        if (flushToken) {
            String token = null;
            try {
                token = next();
            } catch (Exception e) {
                err = e;
                throw e;
            }
            currentToken = token;
            flushToken = false;
        }
        return currentToken;
    }

    /**
     * 丢弃当前 token
     */
    public void pop() {
        flushToken = true;
    }

    /**
     * 生成错误提示信息
     *
     * @return
     */
    public byte[] errStat() {
        byte[] res = new byte[stat.length + 3];
        System.arraycopy(stat, 0, res, 0, pos);                         // SQL 前半部分
        System.arraycopy("<< ".getBytes(), 0, res, pos, 3);              // 错误标记
        System.arraycopy(stat, pos, res, pos + 3, stat.length - pos);   // SQL 后半部分
        return res;
    }

    /**
     * 检查当前位置的字节
     *
     * @return
     */
    private Byte peekByte() {
        if (pos == stat.length) {
            return null;
        }
        return stat[pos];
    }

    /**
     * 把解析位置后移一位
     */
    private void popByte() {
        pos++;
        if (pos > stat.length) {
            pos = stat.length;
        }
    }

    /**
     * 获取下一个 token
     *
     * @return
     * @throws Exception
     */
    private String next() throws Exception {
        if (err != null) {
            throw err;
        }
        return nextMetaState();
    }

    /**
     * 分词状态机
     *
     * @return
     * @throws Exception
     */
    private String nextMetaState() throws Exception {
        // 跳过空白符
        while (true) {
            Byte b = peekByte();
            if (b == null) {
                return "";
            }
            if (!isBlank(b)) {
                break;
            }
            popByte();
        }

        // 判断字符类型，分发解析
        byte b = peekByte();
        if (isSymbol(b)) {
            // 符号：单独成一个 token
            popByte();
            return new String(new byte[]{b});
        } else if (b == '"' || b == '\'') {
            // 引号：进入字符串解析模式
            return nextQuoteState();
        } else if (isAlphaBeta(b) || isDigit(b)) {
            // 字母或数字：进入普通单词解析模式
            return nextTokenState();
        } else {
            // 非法字符：报错
            err = Error.InvalidCommandException;
            throw err;
        }
    }

    /**
     * 读取：连续的字母 + 数字 + 下划线
     *
     * @return
     * @throws Exception
     */
    private String nextTokenState() throws Exception {
        StringBuilder sb = new StringBuilder();
        while (true) {
            Byte b = peekByte();
            if (b == null || !(isAlphaBeta(b) || isDigit(b) || b == '_')) {
                // 空格不属于任何 token，直接吞掉
                if (b != null && isBlank(b)) {
                    popByte();
                }
                return sb.toString();
            }
            sb.append(new String(new byte[]{b}));
            popByte();
        }
    }

    /**
     * 解析字符串
     *
     * @return
     * @throws Exception
     */
    private String nextQuoteState() throws Exception {
        byte quote = peekByte();
        popByte();
        StringBuilder sb = new StringBuilder();
        while (true) {
            Byte b = peekByte();
            if (b == null) {
                err = Error.InvalidCommandException;
                throw err;
            }
            if (b == quote) {
                popByte();
                break;
            }
            sb.append(new String(new byte[]{b}));
            popByte();
        }
        return sb.toString();
    }

    /**
     * 判断数字
     *
     * @param b
     * @return
     */
    static boolean isDigit(byte b) {
        return (b >= '0' && b <= '9');
    }

    /**
     * 判断字母
     *
     * @param b
     * @return
     */
    static boolean isAlphaBeta(byte b) {
        return ((b >= 'a' && b <= 'z') || (b >= 'A' && b <= 'Z'));
    }

    /**
     * 判断符号
     *
     * @param b
     * @return
     */
    static boolean isSymbol(byte b) {
        return (b == '>' || b == '<' || b == '=' || b == '*' ||
                b == ',' || b == '(' || b == ')');
    }

    /**
     * 判断空格
     *
     * @param b
     * @return
     */
    static boolean isBlank(byte b) {
        return (b == '\n' || b == ' ' || b == '\t');
    }
}
