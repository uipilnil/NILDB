package io.github.uipilnil.client;

import io.github.uipilnil.transport.Package;
import io.github.uipilnil.transport.Packager;

/**
 * 客户端传输层工具类
 */
public class RoundTripper {
    private Packager packager;

    public RoundTripper(Packager packager) {
        this.packager = packager;
    }

    /**
     * 处理单次往返请求
     *
     * @param pkg
     * @return
     * @throws Exception
     */
    public Package roundTrip(Package pkg) throws Exception {
        packager.send(pkg);
        return packager.receive();
    }

    /**
     * 释放资源
     *
     * @throws Exception
     */
    public void close() throws Exception {
        packager.close();
    }
}
