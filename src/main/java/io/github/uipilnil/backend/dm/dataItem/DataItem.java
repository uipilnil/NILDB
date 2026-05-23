package io.github.uipilnil.backend.dm.dataItem;

import com.google.common.primitives.Bytes;
import io.github.uipilnil.backend.common.SubArray;
import io.github.uipilnil.backend.dm.DataManagerImpl;
import io.github.uipilnil.backend.dm.page.Page;
import io.github.uipilnil.backend.utils.Parser;
import io.github.uipilnil.backend.utils.Types;

import java.util.Arrays;

public interface DataItem {
    SubArray data();

    void before();

    void unBefore();

    void after(long xid);

    void release();

    void lock();

    void unlock();

    void rLock();

    void rUnLock();

    Page page();

    long getUid();

    byte[] getOldRaw();

    SubArray getRaw();

    /**
     * 拼接数据和数据项头部
     *
     * @param raw
     * @return
     */
    public static byte[] wrapDataItemRaw(byte[] raw) {
        byte[] valid = new byte[1];
        byte[] size = Parser.shortToByte((short) raw.length);
        return Bytes.concat(valid, size, raw);
    }

    /**
     * 把二进制数据项解析为数据项对象
     *
     * @param pg
     * @param offset
     * @param dm
     * @return
     */
    public static DataItem parseDataItem(Page pg, short offset, DataManagerImpl dm) {
        byte[] raw = pg.getData();
        short size = Parser.parseShort(Arrays.copyOfRange(raw, offset + DataItemImpl.OF_SIZE, offset + DataItemImpl.OF_DATA));
        short length = (short) (DataItemImpl.OF_DATA + size);
        long uid = Types.addressToUid(pg.getPageNumber(), offset);
        return new DataItemImpl(new SubArray(raw, offset, offset + length), new byte[length], pg, uid, dm);
    }

    /**
     * 把数据作废，把合法标志位改为 1
     *
     * @param raw
     */
    public static void setDataItemRawInvalid(byte[] raw) {
        raw[DataItemImpl.OF_VALID] = (byte) 1;
    }
}
