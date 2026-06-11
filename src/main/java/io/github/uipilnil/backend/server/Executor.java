package io.github.uipilnil.backend.server;

import io.github.uipilnil.backend.parser.Parser;
import io.github.uipilnil.backend.parser.statement.*;
import io.github.uipilnil.backend.tbm.BeginRes;
import io.github.uipilnil.backend.tbm.TableManager;
import io.github.uipilnil.common.Error;

/**
 * SQL 执行器
 */
public class Executor {
    private long xid;
    TableManager tbm;

    public Executor(TableManager tbm) {
        this.tbm = tbm;
        this.xid = 0;
    }

    /**
     * 回滚未提交事务
     */
    public void close() {
        if (xid != 0) {
            System.out.println("Abnormal Abort: " + xid);
            tbm.abort(xid);
        }
    }

    /**
     * 处理 BEGIN/COMMIT/ABORT
     *
     * @param sql
     * @return
     * @throws Exception
     */
    public byte[] execute(byte[] sql) throws Exception {
        System.out.println("Execute: " + new String(sql));
        Object stat = Parser.Parse(sql);
        if (Begin.class.isInstance(stat)) {
            if (xid != 0) {
                throw Error.NestedTransactionException;
            }
            BeginRes r = tbm.begin((Begin) stat);
            xid = r.xid;
            return r.result;
        } else if (Commit.class.isInstance(stat)) {
            if (xid == 0) {
                throw Error.NoTransactionException;
            }
            byte[] res = tbm.commit(xid);
            xid = 0;
            return res;
        } else if (Abort.class.isInstance(stat)) {
            if (xid == 0) {
                throw Error.NoTransactionException;
            }
            byte[] res = tbm.abort(xid);
            xid = 0;
            return res;
        } else {
            return execute2(stat);
        }
    }

    /**
     * 处理 BEGIN/COMMIT/ABORT 之外的命令
     *
     * @param stat
     * @return
     * @throws Exception
     */
    private byte[] execute2(Object stat) throws Exception {
        boolean tmpTransaction = false;
        Exception e = null;
        if (xid == 0) {
            tmpTransaction = true;
            BeginRes r = tbm.begin(new Begin());
            xid = r.xid;
        }
        try {
            byte[] res = null;
            if (Show.class.isInstance(stat)) {
                res = tbm.show(xid);
            } else if (Create.class.isInstance(stat)) {
                res = tbm.create(xid, (Create) stat);
            } else if (Select.class.isInstance(stat)) {
                res = tbm.read(xid, (Select) stat);
            } else if (Insert.class.isInstance(stat)) {
                res = tbm.insert(xid, (Insert) stat);
            } else if (Delete.class.isInstance(stat)) {
                res = tbm.delete(xid, (Delete) stat);
            } else if (Update.class.isInstance(stat)) {
                res = tbm.update(xid, (Update) stat);
            }
            return res;
        } catch (Exception e1) {
            e = e1;
            throw e;
        } finally {
            if (tmpTransaction) {
                if (e != null) {
                    tbm.abort(xid);
                } else {
                    tbm.commit(xid);
                }
                xid = 0;
            }
        }
    }
}
