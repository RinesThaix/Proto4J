package sexy.kostya.proto4j.transport.buffer;

import io.netty.buffer.ByteBuf;
import sexy.kostya.proto4j.transport.util.Recycler;

/**
 * Created by k.shandurenko on 30.09.2020
 */
public class BufferImpl implements Buffer {

    static final Recycler<BufferImpl> RECYCLER = new Recycler<>(BufferImpl::new);

    ByteBuf buffer;

    public void setHandle(ByteBuf buffer) {
        this.buffer = buffer;
    }

    public ByteBuf getHandle() {
        return this.buffer;
    }

    @Override
    public void skip(int length) {
        this.buffer.skipBytes(length);
    }

    @Override
    public int readableBytes() {
        return this.buffer.readableBytes();
    }

    @Override
    public byte readByte() {
        return this.buffer.readByte();
    }

    @Override
    public void writeByte(byte value) {
        this.buffer.writeByte(value);
    }

    @Override
    public void readBytes(byte[] array) {
        this.buffer.readBytes(array);
    }

    @Override
    public void writeBytes(byte[] value) {
        this.buffer.writeBytes(value);
    }

    @Override
    public short readShort() {
        return this.buffer.readShort();
    }

    @Override
    public void writeShort(short value) {
        this.buffer.writeShort(value);
    }

    @Override
    public int readInt() {
        return this.buffer.readInt();
    }

    @Override
    public void writeInt(int value) {
        this.buffer.writeInt(value);
    }

    @Override
    public long readLong() {
        return this.buffer.readLong();
    }

    @Override
    public void writeLong(long value) {
        this.buffer.writeLong(value);
    }

    @Override
    public void release() {
        this.buffer.release();
        this.buffer = null;
        RECYCLER.recycle(this);
    }

}
