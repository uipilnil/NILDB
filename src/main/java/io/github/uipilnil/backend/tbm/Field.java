package io.github.uipilnil.backend.tbm;

import com.google.common.primitives.Bytes;
import io.github.uipilnil.backend.im.BPlusTree;
import io.github.uipilnil.backend.parser.statement.SingleExpression;
import io.github.uipilnil.backend.tm.TransactionManagerImpl;
import io.github.uipilnil.backend.utils.Panic;
import io.github.uipilnil.backend.utils.ParseStringRes;
import io.github.uipilnil.backend.utils.Parser;
import io.github.uipilnil.common.Error;

import java.util.Arrays;
import java.util.List;

/**
 * 字段信息管理器
 * 格式：[FieldName] [TypeName] [IndexUid]
 * IndexUid 默认 0（无索引）
 */
public class Field {
    long uid;
    private Table tb;
    String fieldName;
    String fieldType;
    private long index;
    private BPlusTree bt;

    public Field(long uid, Table tb) {
        this.uid = uid;
        this.tb = tb;
    }

    public Field(Table tb, String fieldName, String fieldType, long index) {
        this.tb = tb;
        this.fieldName = fieldName;
        this.fieldType = fieldType;
        this.index = index;
    }

    /**
     * 创建字段
     *
     * @param tb
     * @param xid
     * @param fieldName
     * @param fieldType
     * @param indexed   是否需要索引
     * @return
     * @throws Exception
     */
    public static Field createField(Table tb, long xid, String fieldName, String fieldType, boolean indexed) throws Exception {
        typeCheck(fieldType);
        Field f = new Field(tb, fieldName, fieldType, 0);
        if (indexed) {
            long index = BPlusTree.create(((TableManagerImpl) tb.tbm).dm);
            BPlusTree bt = BPlusTree.load(index, ((TableManagerImpl) tb.tbm).dm);
            f.index = index;
            f.bt = bt;
        }
        f.persistSelf(xid);
        return f;
    }

    /**
     * 把字段写入磁盘
     *
     * @param xid
     * @throws Exception
     */
    private void persistSelf(long xid) throws Exception {
        byte[] nameRaw = Parser.string2Byte(fieldName);
        byte[] typeRaw = Parser.string2Byte(fieldType);
        byte[] indexRaw = Parser.long2Byte(index);
        this.uid = ((TableManagerImpl) tb.tbm).vm.insert(xid, Bytes.concat(nameRaw, typeRaw, indexRaw));
    }

    /**
     * 获取字段
     *
     * @param tb
     * @param uid
     * @return
     */
    public static Field loadField(Table tb, long uid) {
        byte[] raw = null;
        try {
            raw = ((TableManagerImpl) tb.tbm).vm.read(TransactionManagerImpl.SUPER_XID, uid);
        } catch (Exception e) {
            Panic.panic(e);
        }
        assert raw != null;
        return new Field(uid, tb).parseSelf(raw);
    }

    /**
     * 检查字段合法性
     *
     * @param fieldType
     * @throws Exception
     */
    private static void typeCheck(String fieldType) throws Exception {
        if (!"int32".equals(fieldType) && !"int64".equals(fieldType) && !"string".equals(fieldType)) {
            throw Error.InvalidFieldException;
        }
    }

    /**
     * 把二进制数据反序列化为字段对象
     *
     * @param raw
     * @return
     */
    private Field parseSelf(byte[] raw) {
        int position = 0;

        ParseStringRes res = Parser.parseString(raw);
        fieldName = res.str;
        position += res.next;

        res = Parser.parseString(Arrays.copyOfRange(raw, position, raw.length));
        fieldType = res.str;
        position += res.next;

        this.index = Parser.parseLong(Arrays.copyOfRange(raw, position, position + 8));
        if (index != 0) {
            try {
                bt = BPlusTree.load(index, ((TableManagerImpl) tb.tbm).dm);
            } catch (Exception e) {
                Panic.panic(e);
            }
        }
        return this;
    }

    /**
     * 判断字段是否有索引
     *
     * @return
     */
    public boolean isIndexed() {
        return index != 0;
    }

    /**
     * 向字段索引插入值
     *
     * @param key
     * @param uid
     * @throws Exception
     */
    public void insert(Object key, long uid) throws Exception {
        long uKey = value2Uid(key);
        bt.insert(uKey, uid);
    }

    /**
     * 范围查询索引
     *
     * @param left
     * @param right
     * @return
     * @throws Exception
     */
    public List<Long> search(long left, long right) throws Exception {
        return bt.searchRange(left, right);
    }

    /**
     * SQL 中的字符串转 Java 对象
     *
     * @param str
     * @return
     */
    public Object string2Value(String str) {
        switch (fieldType) {
            case "int32":
                return Integer.parseInt(str);
            case "int64":
                return Long.parseLong(str);
            case "string":
                return str;
        }
        return null;
    }

    /**
     * 任意类型字段转 long
     *
     * @param key
     * @return
     */
    public long value2Uid(Object key) {
        long uid = 0;
        switch (fieldType) {
            case "string":
                uid = Parser.string2Uid((String) key);
                break;
            case "int32":
                int uint = (int) key;
                return (long) uint;
            case "int64":
                uid = (long) key;
                break;
        }
        return uid;
    }

    /**
     * Java 对象转字节数组
     *
     * @param v
     * @return
     */
    public byte[] value2Raw(Object v) {
        byte[] raw = null;
        switch (fieldType) {
            case "int32":
                raw = Parser.int2Byte((int) v);
                break;
            case "int64":
                raw = Parser.long2Byte((long) v);
                break;
            case "string":
                raw = Parser.string2Byte((String) v);
                break;
        }
        return raw;
    }

    /**
     * Java 对象包装器
     */
    class ParseValueRes {
        Object v;
        int shift;
    }

    /**
     * 字节数组转 Java 对象
     *
     * @param raw
     * @return
     */
    public ParseValueRes parserValue(byte[] raw) {
        ParseValueRes res = new ParseValueRes();
        switch (fieldType) {
            case "int32":
                res.v = Parser.parseInt(Arrays.copyOf(raw, 4));
                res.shift = 4;
                break;
            case "int64":
                res.v = Parser.parseLong(Arrays.copyOf(raw, 8));
                res.shift = 8;
                break;
            case "string":
                ParseStringRes r = Parser.parseString(raw);
                res.v = r.str;
                res.shift = r.next;
                break;
        }
        return res;
    }

    /**
     * 打印查询结果
     *
     * @param v
     * @return
     */
    public String printValue(Object v) {
        String str = null;
        switch (fieldType) {
            case "int32":
                str = String.valueOf((int) v);
                break;
            case "int64":
                str = String.valueOf((long) v);
                break;
            case "string":
                str = (String) v;
                break;
        }
        return str;
    }

    /**
     * where 条件转查询区间
     *
     * @param exp
     * @return
     * @throws Exception
     */
    public FieldCalRes calExp(SingleExpression exp) throws Exception {
        Object v = null;
        FieldCalRes res = new FieldCalRes();
        switch (exp.compareOp) {
            case "<":
                res.left = 0;
                v = string2Value(exp.value);
                res.right = value2Uid(v);
                if (res.right > 0) {
                    res.right--;
                }
                break;
            case "=":
                v = string2Value(exp.value);
                res.left = value2Uid(v);
                res.right = res.left;
                break;
            case ">":
                res.right = Long.MAX_VALUE;
                v = string2Value(exp.value);
                res.left = value2Uid(v) + 1;
                break;
        }
        return res;
    }

    @Override
    public String toString() {
        return new StringBuilder("(")
                .append(fieldName)
                .append(", ")
                .append(fieldType)
                .append(index != 0 ? ", Index" : ", NoIndex")
                .append(")")
                .toString();
    }
}
