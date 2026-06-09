package io.github.uipilnil.backend.im;

import io.github.uipilnil.backend.common.SubArray;
import io.github.uipilnil.backend.dm.dataItem.DataItem;
import io.github.uipilnil.backend.tm.TransactionManagerImpl;
import io.github.uipilnil.backend.utils.Parser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * B+ 树节点
 * 结构：
 * 1. 头部：[LeafFlag] [KeyNumber] [SiblingUid]
 * 2. 数据区：[Son0] [Key0] [Son1] [Key1] ... [SonN] [KeyN]
 */
public class Node {
    static final int IS_LEAF_OFFSET = 0;                    // 叶节点标记起始偏移
    static final int NO_KEYS_OFFSET = IS_LEAF_OFFSET + 1;   // 键数量起始偏移
    static final int SIBLING_OFFSET = NO_KEYS_OFFSET + 2;   // 兄弟节点 UID 起始偏移
    static final int NODE_HEADER_SIZE = SIBLING_OFFSET + 8; // 节点头部总大小

    static final int BALANCE_NUMBER = 32;                   // B+ 树平衡因子
    static final int NODE_SIZE = NODE_HEADER_SIZE + (2 * 8) * (BALANCE_NUMBER * 2 + 2); // 节点总大小

    BPlusTree tree;
    DataItem dataItem;
    SubArray raw;
    long uid;

    /**
     * 修改叶节点标记
     *
     * @param raw
     * @param isLeaf
     */
    static void setRawIsLeaf(SubArray raw, boolean isLeaf) {
        if (isLeaf) {
            raw.raw[raw.start + IS_LEAF_OFFSET] = (byte) 1;
        } else {
            raw.raw[raw.start + IS_LEAF_OFFSET] = (byte) 0;
        }
    }

    /**
     * 获取叶节点标记
     *
     * @param raw
     * @return
     */
    static boolean getRawIfLeaf(SubArray raw) {
        return raw.raw[raw.start + IS_LEAF_OFFSET] == (byte) 1;
    }

    /**
     * 写入键数量
     *
     * @param raw
     * @param noKeys
     */
    static void setRawNoKeys(SubArray raw, int noKeys) {
        System.arraycopy(Parser.short2Byte((short) noKeys), 0, raw.raw, raw.start + NO_KEYS_OFFSET, 2);
    }

    /**
     * 获取键数量
     *
     * @param raw
     * @return
     */
    static int getRawNoKeys(SubArray raw) {
        return (int) Parser.parseShort(Arrays.copyOfRange(raw.raw, raw.start + NO_KEYS_OFFSET, raw.start + NO_KEYS_OFFSET + 2));
    }

    /**
     * 修改兄弟节点 UID
     *
     * @param raw
     * @param sibling
     */
    static void setRawSibling(SubArray raw, long sibling) {
        System.arraycopy(Parser.long2Byte(sibling), 0, raw.raw, raw.start + SIBLING_OFFSET, 8);
    }

    /**
     *
     * 获取兄弟节点 UID     * @param raw
     *
     * @return
     */
    static long getRawSibling(SubArray raw) {
        return Parser.parseLong(Arrays.copyOfRange(raw.raw, raw.start + SIBLING_OFFSET, raw.start + SIBLING_OFFSET + 8));
    }

    /**
     * 写入第 k 个子节点的 UID
     *
     * @param raw
     * @param uid
     * @param kth
     */
    static void setRawKthSon(SubArray raw, long uid, int kth) {
        int offset = raw.start + NODE_HEADER_SIZE + kth * (8 * 2);
        System.arraycopy(Parser.long2Byte(uid), 0, raw.raw, offset, 8);
    }

    /**
     * 获取第 k 个子节点的 UID
     *
     * @param raw
     * @param kth
     * @return
     */
    static long getRawKthSon(SubArray raw, int kth) {
        int offset = raw.start + NODE_HEADER_SIZE + kth * (8 * 2);
        return Parser.parseLong(Arrays.copyOfRange(raw.raw, offset, offset + 8));
    }

    /**
     * 写入第 k 个 key
     *
     * @param raw
     * @param key
     * @param kth
     */
    static void setRawKthKey(SubArray raw, long key, int kth) {
        int offset = raw.start + NODE_HEADER_SIZE + kth * (8 * 2) + 8;
        System.arraycopy(Parser.long2Byte(key), 0, raw.raw, offset, 8);
    }

    /**
     * 获取第 k 个 key
     *
     * @param raw
     * @param kth
     * @return
     */
    static long getRawKthKey(SubArray raw, int kth) {
        int offset = raw.start + NODE_HEADER_SIZE + kth * (8 * 2) + 8;
        return Parser.parseLong(Arrays.copyOfRange(raw.raw, offset, offset + 8));
    }

    /**
     * 从第 k 位开始批量拷贝数据
     *
     * @param from
     * @param to
     * @param kth
     */
    static void copyRawFromKth(SubArray from, SubArray to, int kth) {
        int offset = from.start + NODE_HEADER_SIZE + kth * (8 * 2);
        System.arraycopy(from.raw, offset, to.raw, to.start + NODE_HEADER_SIZE, from.end - offset);
    }

    /**
     * 从第 k 位开始向后挪动数据
     *
     * @param raw
     * @param kth
     */
    static void shiftRawKth(SubArray raw, int kth) {
        int begin = raw.start + NODE_HEADER_SIZE + (kth + 1) * (8 * 2);
        int end = raw.start + NODE_SIZE - 1;
        for (int i = end; i >= begin; i--) {
            raw.raw[i] = raw.raw[i - (8 * 2)];
        }
    }

    /**
     * 创建根节点的原始字节数组
     *
     * @param left
     * @param right
     * @param key
     * @return
     */
    static byte[] newRootRaw(long left, long right, long key) {
        SubArray raw = new SubArray(new byte[NODE_SIZE], 0, NODE_SIZE);

        setRawIsLeaf(raw, false);
        setRawNoKeys(raw, 2);
        setRawSibling(raw, 0);

        // 设置第 0 个键值对（根节点的左子节点）
        setRawKthSon(raw, left, 0);
        setRawKthKey(raw, key, 0);

        // 设置第 1 个键值对（根节点的右子节点）
        setRawKthSon(raw, right, 1);
        setRawKthKey(raw, Long.MAX_VALUE, 1);

        return raw.raw;
    }

    /**
     * 创建空的根节点
     *
     * @return
     */
    static byte[] newNilRootRaw() {
        SubArray raw = new SubArray(new byte[NODE_SIZE], 0, NODE_SIZE);

        setRawIsLeaf(raw, true);
        setRawNoKeys(raw, 0);
        setRawSibling(raw, 0);

        return raw.raw;
    }

    /**
     * 从磁盘加载节点数组，并封装成 Node 对象
     *
     * @param bTree
     * @param uid
     * @return
     * @throws Exception
     */
    static Node loadNode(BPlusTree bTree, long uid) throws Exception {
        DataItem di = bTree.dm.read(uid);
        assert di != null;
        Node n = new Node();
        n.tree = bTree;
        n.dataItem = di;
        n.raw = di.data();
        n.uid = uid;
        return n;
    }

    /**
     * 释放数据项资源
     */
    public void release() {
        dataItem.release();
    }

    /**
     * 判断叶子节点
     *
     * @return
     */
    public boolean isLeaf() {
        dataItem.rLock();
        try {
            return getRawIfLeaf(raw);
        } finally {
            dataItem.rUnLock();
        }
    }

    /**
     * 非叶子节点搜索结果封装类
     */
    class SearchNextRes {
        long uid;
        long siblingUid;
    }

    /**
     * 非叶子节点的搜索
     *
     * @param key
     * @return
     */
    public SearchNextRes searchNext(long key) {
        dataItem.rLock();
        try {
            SearchNextRes res = new SearchNextRes();
            int noKeys = getRawNoKeys(raw);
            for (int i = 0; i < noKeys; i++) {
                long ik = getRawKthKey(raw, i);
                if (key < ik) {
                    res.uid = getRawKthSon(raw, i);
                    res.siblingUid = 0;
                    return res;
                }
            }

            // 当前节点所有 key 都比目标小，返回右兄弟节点 UID
            res.uid = 0;
            res.siblingUid = getRawSibling(raw);
            return res;

        } finally {
            dataItem.rUnLock();
        }
    }

    /**
     * 叶节点搜索结果封装类
     */
    class LeafSearchRangeRes {
        List<Long> uids;
        long siblingUid;
    }

    /**
     * 叶子节点的搜索
     *
     * @param leftKey
     * @param rightKey
     * @return
     */
    public LeafSearchRangeRes leafSearchRange(long leftKey, long rightKey) {
        dataItem.rLock();
        try {
            int noKeys = getRawNoKeys(raw);
            int kth = 0;
            while (kth < noKeys) {
                long ik = getRawKthKey(raw, kth);
                if (ik >= leftKey) {
                    break;
                }
                kth++;
            }

            List<Long> uids = new ArrayList<>();
            while (kth < noKeys) {
                long ik = getRawKthKey(raw, kth);
                if (ik <= rightKey) {
                    uids.add(getRawKthSon(raw, kth));
                    kth++;
                } else {
                    break;
                }
            }

            // 查完当前节点，去右兄弟节点继续查
            long siblingUid = 0;
            if (kth == noKeys) {
                siblingUid = getRawSibling(raw);
            }

            LeafSearchRangeRes res = new LeafSearchRangeRes();
            res.uids = uids;
            res.siblingUid = siblingUid;
            return res;
        } finally {
            dataItem.rUnLock();
        }
    }

    /**
     * 插入和分裂操作结果封装类
     */
    class InsertAndSplitRes {
        long siblingUid, newSon, newKey;
    }

    /**
     * 插入和分裂操作
     *
     * @param uid
     * @param key
     * @return
     * @throws Exception
     */
    public InsertAndSplitRes insertAndSplit(long uid, long key) throws Exception {
        boolean success = false;    // 插入成功标记
        Exception err = null;
        InsertAndSplitRes res = new InsertAndSplitRes();

        dataItem.before();
        try {
            success = insert(uid, key);

            // 未插入成功，去右兄弟节点插入
            if (!success) {
                res.siblingUid = getRawSibling(raw);
                return res;
            }

            // 插入后节点已满，需要分裂
            if (needSplit()) {
                try {
                    SplitRes r = split();
                    res.newSon = r.newSon;
                    res.newKey = r.newKey;
                    return res;
                } catch (Exception e) {
                    err = e;
                    throw e;
                }
            } else {
                return res;
            }
        } finally {
            if (err == null && success) {
                dataItem.after(TransactionManagerImpl.SUPER_XID);
            } else {
                dataItem.unBefore();
            }
        }
    }

    /**
     * 插入操作
     *
     * @param uid
     * @param key
     * @return
     */
    private boolean insert(long uid, long key) {
        int noKeys = getRawNoKeys(raw);
        int kth = 0;
        while (kth < noKeys) {
            long ik = getRawKthKey(raw, kth);
            if (ik < key) {
                kth++;
            } else {
                break;
            }
        }
        if (kth == noKeys && getRawSibling(raw) != 0) return false;

        if (getRawIfLeaf(raw)) {
            shiftRawKth(raw, kth);
            setRawKthKey(raw, key, kth);
            setRawKthSon(raw, uid, kth);
            setRawNoKeys(raw, noKeys + 1);
        } else {
            long kk = getRawKthKey(raw, kth);
            setRawKthKey(raw, key, kth);
            shiftRawKth(raw, kth + 1);
            setRawKthKey(raw, kk, kth + 1);
            setRawKthSon(raw, uid, kth + 1);
            setRawNoKeys(raw, noKeys + 1);
        }
        return true;
    }

    /**
     * 判断节点需要分裂
     *
     * @return
     */
    private boolean needSplit() {
        return BALANCE_NUMBER * 2 == getRawNoKeys(raw);
    }

    /**
     * 节点分裂结果封装类
     */
    class SplitRes {
        long newSon, newKey;
    }

    /**
     * 分裂操作
     *
     * @return
     * @throws Exception
     */
    private SplitRes split() throws Exception {
        SubArray nodeRaw = new SubArray(new byte[NODE_SIZE], 0, NODE_SIZE);
        setRawIsLeaf(nodeRaw, getRawIfLeaf(raw));
        setRawNoKeys(nodeRaw, BALANCE_NUMBER);
        setRawSibling(nodeRaw, getRawSibling(raw));
        copyRawFromKth(raw, nodeRaw, BALANCE_NUMBER);
        long son = tree.dm.insert(TransactionManagerImpl.SUPER_XID, nodeRaw.raw);
        setRawNoKeys(raw, BALANCE_NUMBER);
        setRawSibling(raw, son);

        SplitRes res = new SplitRes();
        res.newSon = son;
        res.newKey = getRawKthKey(nodeRaw, 0);
        return res;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Is leaf: ").append(getRawIfLeaf(raw)).append("\n");
        int KeyNumber = getRawNoKeys(raw);
        sb.append("KeyNumber: ").append(KeyNumber).append("\n");
        sb.append("sibling: ").append(getRawSibling(raw)).append("\n");
        for (int i = 0; i < KeyNumber; i++) {
            sb.append("son: ").append(getRawKthSon(raw, i)).append(", key: ").append(getRawKthKey(raw, i)).append("\n");
        }
        return sb.toString();
    }
}
