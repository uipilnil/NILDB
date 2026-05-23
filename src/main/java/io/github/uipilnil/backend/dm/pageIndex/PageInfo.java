package io.github.uipilnil.backend.dm.pageIndex;

/**
 * 页索引树的节点
 */
public class PageInfo {
    public int pgno;
    public int freeSpace;

    public PageInfo(int pgno, int freeSpace) {
        this.pgno = pgno;
        this.freeSpace = freeSpace;
    }
}
