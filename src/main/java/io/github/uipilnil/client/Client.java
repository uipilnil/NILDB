package io.github.uipilnil.client;

import io.github.uipilnil.transport.Package;
import io.github.uipilnil.transport.Packager;

/**
 * 客户端入口
 */
public class Client {
    private RoundTripper rt;

    public Client(Packager packager) {
        this.rt = new RoundTripper(packager);
    }

    /**
     * 执行 SQL
     *
     * @param stat
     * @return
     * @throws Exception
     */
    public byte[] execute(byte[] stat) throws Exception {
        Package pkg = new Package(stat, null);
        Package resPkg = rt.roundTrip(pkg);
        if (resPkg.getErr() != null) {
            throw resPkg.getErr();
        }
        return resPkg.getData();
    }

    /**
     * 释放资源
     */
    public void close() {
        try {
            rt.close();
        } catch (Exception e) {
        }
    }
}
