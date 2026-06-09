package io.github.uipilnil.backend.im;

import io.github.uipilnil.backend.common.SubArray;
import io.github.uipilnil.backend.dm.DataManager;
import io.github.uipilnil.backend.dm.dataItem.DataItem;
import io.github.uipilnil.backend.im.Node.InsertAndSplitRes;
import io.github.uipilnil.backend.im.Node.LeafSearchRangeRes;
import io.github.uipilnil.backend.im.Node.SearchNextRes;
import io.github.uipilnil.backend.tm.TransactionManagerImpl;
import io.github.uipilnil.backend.utils.Parser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * B+ 树
 */
public class BPlusTree {
    DataManager dm;
    long bootUid;               // 引导块 UID，存储根节点地址
    DataItem bootDataItem;      // 引导块数据，存储根节点 UID
    Lock bootLock;

    /**
     * 创建 B+ 树
     *
     * @param dm
     * @return
     * @throws Exception
     */
    public static long create(DataManager dm) throws Exception {
        byte[] rawRoot = Node.newNilRootRaw();
        long rootUid = dm.insert(TransactionManagerImpl.SUPER_XID, rawRoot);
        return dm.insert(TransactionManagerImpl.SUPER_XID, Parser.long2Byte(rootUid));
    }

    /**
     * 加载 B+ 树
     *
     * @param bootUid
     * @param dm
     * @return
     * @throws Exception
     */
    public static BPlusTree load(long bootUid, DataManager dm) throws Exception {
        DataItem bootDataItem = dm.read(bootUid);
        assert bootDataItem != null;
        BPlusTree t = new BPlusTree();
        t.bootUid = bootUid;
        t.dm = dm;
        t.bootDataItem = bootDataItem;
        t.bootLock = new ReentrantLock();
        return t;
    }

    /**
     * 获取根节点
     *
     * @return
     */
    private long rootUid() {
        bootLock.lock();
        try {
            SubArray sa = bootDataItem.data();
            return Parser.parseLong(Arrays.copyOfRange(sa.raw, sa.start, sa.start + 8));
        } finally {
            bootLock.unlock();
        }
    }

    /**
     * 更新根节点
     *
     * @param left
     * @param right
     * @param rightKey
     * @throws Exception
     */
    private void updateRootUid(long left, long right, long rightKey) throws Exception {
        bootLock.lock();
        try {
            byte[] rootRaw = Node.newRootRaw(left, right, rightKey);
            long newRootUid = dm.insert(TransactionManagerImpl.SUPER_XID, rootRaw);
            bootDataItem.before();
            SubArray diRaw = bootDataItem.data();
            System.arraycopy(Parser.long2Byte(newRootUid), 0, diRaw.raw, diRaw.start, 8);
            bootDataItem.after(TransactionManagerImpl.SUPER_XID);
        } finally {
            bootLock.unlock();
        }
    }

    /**
     * 查找 key 所在叶子结点
     *
     * @param nodeUid
     * @param key
     * @return
     * @throws Exception
     */
    private long searchLeaf(long nodeUid, long key) throws Exception {
        Node node = Node.loadNode(this, nodeUid);
        boolean isLeaf = node.isLeaf();
        node.release();

        if (isLeaf) {
            return nodeUid;
        } else {
            long next = searchNext(nodeUid, key);
            return searchLeaf(next, key);
        }
    }

    /**
     * 查找下一层需要访问的叶子结点
     *
     * @param nodeUid
     * @param key
     * @return
     * @throws Exception
     */
    private long searchNext(long nodeUid, long key) throws Exception {
        while (true) {
            Node node = Node.loadNode(this, nodeUid);
            SearchNextRes res = node.searchNext(key);
            node.release();
            if (res.uid != 0) return res.uid;
            nodeUid = res.siblingUid;
        }
    }

    /**
     * 查询
     *
     * @param key
     * @return
     * @throws Exception
     */
    public List<Long> search(long key) throws Exception {
        return searchRange(key, key);
    }

    /**
     * 范围查询
     *
     * @param leftKey
     * @param rightKey
     * @return
     * @throws Exception
     */
    public List<Long> searchRange(long leftKey, long rightKey) throws Exception {
        long rootUid = rootUid();
        long leafUid = searchLeaf(rootUid, leftKey);
        List<Long> uids = new ArrayList<>();
        while (true) {
            Node leaf = Node.loadNode(this, leafUid);
            LeafSearchRangeRes res = leaf.leafSearchRange(leftKey, rightKey);
            leaf.release();
            uids.addAll(res.uids);
            if (res.siblingUid == 0) {
                break;
            } else {
                leafUid = res.siblingUid;
            }
        }
        return uids;
    }

    /**
     * 对外提供插入接口
     *
     * @param key
     * @param uid
     * @throws Exception
     */
    public void insert(long key, long uid) throws Exception {
        long rootUid = rootUid();
        InsertRes res = insert(rootUid, uid, key);
        assert res != null;
        if (res.newNode != 0) {
            updateRootUid(rootUid, res.newNode, res.newKey);
        }
    }

    /**
     * 插入结果封装类
     */
    class InsertRes {
        long newNode, newKey;
    }

    /**
     * 插入操作
     *
     * @param nodeUid
     * @param uid
     * @param key
     * @return
     * @throws Exception
     */
    private InsertRes insert(long nodeUid, long uid, long key) throws Exception {
        Node node = Node.loadNode(this, nodeUid);
        boolean isLeaf = node.isLeaf();
        node.release();

        InsertRes res = null;
        if (isLeaf) {
            res = insertAndSplit(nodeUid, uid, key);
        } else {
            long next = searchNext(nodeUid, key);
            InsertRes ir = insert(next, uid, key);
            if (ir.newNode != 0) {
                res = insertAndSplit(nodeUid, ir.newNode, ir.newKey);
            } else {
                res = new InsertRes();
            }
        }
        return res;
    }

    /**
     * 插入和分裂操作
     *
     * @param nodeUid
     * @param uid
     * @param key
     * @return
     * @throws Exception
     */
    private InsertRes insertAndSplit(long nodeUid, long uid, long key) throws Exception {
        while (true) {
            Node node = Node.loadNode(this, nodeUid);
            InsertAndSplitRes iasr = node.insertAndSplit(uid, key);
            node.release();
            if (iasr.siblingUid != 0) {
                nodeUid = iasr.siblingUid;
            } else {
                InsertRes res = new InsertRes();
                res.newNode = iasr.newSon;
                res.newKey = iasr.newKey;
                return res;
            }
        }
    }

    /**
     * 关闭资源
     */
    public void close() {
        bootDataItem.release();
    }
}
