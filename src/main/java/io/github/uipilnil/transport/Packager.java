package io.github.uipilnil.transport;

/**
 * 数据包收发器
 */
public class Packager {
    private Transporter transpoter;
    private Encoder encoder;

    public Packager(Transporter transpoter, Encoder encoder) {
        this.transpoter = transpoter;
        this.encoder = encoder;
    }

    /**
     * 发送数据包
     *
     * @param pkg
     * @throws Exception
     */
    public void send(Package pkg) throws Exception {
        byte[] data = encoder.encode(pkg);
        transpoter.send(data);
    }

    /**
     * 接收网络数据包
     *
     * @return
     * @throws Exception
     */
    public Package receive() throws Exception {
        byte[] data = transpoter.receive();
        return encoder.decode(data);
    }

    /**
     * 释放资源
     *
     * @throws Exception
     */
    public void close() throws Exception {
        transpoter.close();
    }
}
