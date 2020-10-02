package sexy.kostya.proto4j.transport.buffer;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Created by k.shandurenko on 30.09.2020
 */
public interface Buffer extends AutoCloseable {

    static Buffer newBuffer() {
        return wrap(Unpooled.buffer());
    }

    static Buffer newBuffer(int maxCapacity) {
        return wrap(Unpooled.buffer(maxCapacity, maxCapacity));
    }

    static Buffer wrap(byte[] bytes) {
        return wrap(Unpooled.wrappedBuffer(bytes));
    }

    static Buffer wrap(ByteBuf buffer) {
        BufferImpl result = BufferImpl.RECYCLER.acquire();
        result.setHandle(buffer);
        return result;
    }

    default void write(Buffer buffer) {
        ((BufferImpl) this).getHandle().writeBytes(((BufferImpl) buffer).getHandle());
        buffer.release();
    }

    void skip(int length);

    int readableBytes();

    byte readByte();

    void writeByte(byte value);

    void readBytes(byte[] array);

    void writeBytes(byte[] value);

    default boolean readBoolean() {
        return readByte() == 1;
    }

    default void writeBoolean(boolean value) {
        writeByte((byte) (value ? 1 : 0));
    }

    short readShort();

    void writeShort(short value);

    int readInt();

    void writeInt(int value);

    long readLong();

    void writeLong(long value);

    default int readVarInt() {
        int tmp;
        if ((tmp = this.readByte()) >= 0) {
            return tmp;
        }
        int result = tmp & 0x7f;
        if ((tmp = this.readByte()) >= 0) {
            result |= tmp << 7;
        } else {
            result |= (tmp & 0x7f) << 7;
            if ((tmp = this.readByte()) >= 0) {
                result |= tmp << 14;
            } else {
                result |= (tmp & 0x7f) << 14;
                if ((tmp = this.readByte()) >= 0) {
                    result |= tmp << 21;
                } else {
                    result |= (tmp & 0x7f) << 21;
                    result |= this.readByte() << 28;
                }
            }
        }
        return result;
    }

    default void writeVarInt(int value) {
        while (true) {
            int bits = value & 0x7f;
            value >>>= 7;
            if (value == 0) {
                this.writeByte((byte) bits);
                return;
            }
            this.writeByte((byte) (bits | 0x80));
        }
    }

    default long readVarLong() {
        long value = 0;
        byte temp;
        for (int i = 0; i < 10; i++) {
            temp = this.readByte();
            value |= ((long) (temp & 0x7F)) << (i * 7);
            if ((temp & 0x80) != 0x80) {
                break;
            }
        }
        return value;
    }

    default void writeVarLong(long value) {
        byte temp;
        do {
            temp = (byte) (value & 0x7F);
            value >>>= 7;
            if (value != 0) {
                temp |= 0x80;
            }
            this.writeByte(temp);
        } while (value != 0);
    }

    default String readString() {
        int length;
        int lengthByte = ((int) readByte()) & 0xff;
        if (lengthByte <= 253) {
            length = lengthByte;
        } else if (lengthByte == 255) {
            throw new IllegalStateException("Invalid string header");
        } else {
            // multi byte length
            length = ((int) readByte() & 0xff) | (((int) readByte() & 0xff) << 8) | (((int) readByte() & 0xff) << 16);
        }
        if (length >= (1 << 24)) {
            throw new IllegalStateException("String is too long");
        }
        if (length < 0) {
            throw new IllegalStateException("String can't have negative length " + length);
        }
        byte[] result = new byte[length];
        readBytes(result);
        int padding = getStringPadding(length);
        if (padding > 0) {
            skip(padding);
        }
        return new String(result);
    }

    default void writeString(String value) {
        byte[] val = value.getBytes();
        if (val.length < 253) {
            writeByte((byte) val.length);
        } else if (val.length < (1 << 24)) {
            writeByte((byte) 0xfe);
            writeByte((byte) (val.length & 0xff));
            writeByte((byte) ((val.length >> 8) & 0xff));
            writeByte((byte) ((val.length >> 16) & 0xff));
        } else {
            throw new IllegalArgumentException("String is too long");
        }
        writeBytes(val);
        int padding = getStringPadding(val.length);
        for (int i = 0; i < padding; ++i) {
            writeByte((byte) 0);
        }
    }

    default String readStringMaybe() {
        return readBoolean() ? readString() : null;
    }

    default void writeStringMaybe(String value) {
        if (value == null) {
            writeBoolean(false);
        } else {
            writeBoolean(true);
            writeString(value);
        }
    }

    default UUID readUUID() {
        return new UUID(readLong(), readLong());
    }

    default void writeUUID(UUID uuid) {
        writeLong(uuid.getMostSignificantBits());
        writeLong(uuid.getLeastSignificantBits());
    }

    default UUID readUUIDMaybe() {
        return readBoolean() ? readUUID() : null;
    }

    default void writeUUIDMaybe(UUID uuid) {
        if (uuid == null) {
            writeBoolean(false);
        } else {
            writeBoolean(true);
            writeUUID(uuid);
        }
    }

    default <E extends Enum> E readEnum(Class<E> enumClass) {
        return enumClass.getEnumConstants()[readVarInt()];
    }

    default <E extends Enum> void writeEnum(E value) {
        writeVarInt(value.ordinal());
    }

    default <T, C extends Collection<T>> C readCollection(Function<Integer, C> generator, Supplier<T> reader) {
        int size       = readVarInt();
        C   collection = generator.apply(size);
        for (int i = 0; i < size; ++i) {
            collection.add(reader.get());
        }
        return collection;
    }

    default <T> List<T> readArrayList(Supplier<T> reader) {
        return readCollection(ArrayList::new, reader);
    }

    default <T> Set<T> readHashSet(Supplier<T> reader) {
        return readCollection(HashSet::new, reader);
    }

    default <T, C extends Collection<T>> void writeCollection(C collection, Consumer<T> writer) {
        int size = collection.size();
        writeVarInt(size);
        collection.forEach(writer);
    }

    default <K, V, M extends Map<K, V>> M readMap(Function<Integer, M> generator, Supplier<K> keyReader, Supplier<V> valueReader) {
        int size = readVarInt();
        M   map  = generator.apply(size);
        for (int i = 0; i < size; ++i) {
            map.put(keyReader.get(), valueReader.get());
        }
        return map;
    }

    default <K, V> Map<K, V> readHashMap(Supplier<K> keyReader, Supplier<V> valueReader) {
        return readMap(HashMap::new, keyReader, valueReader);
    }

    default <K, V, M extends Map<K, V>> void writeMap(M map, Consumer<K> keyWriter, Consumer<V> valueWriter) {
        writeVarInt(map.size());
        map.forEach((k, v) -> {
            keyWriter.accept(k);
            valueWriter.accept(v);
        });
    }

    void release();

    @Override
    default void close() {
        release();
    }

    static int getStringPadding(int length) {
        int padding = length + 1;
        if (length > 253) {
            padding += 3;
        }
        padding %= 4;
        if (padding > 0) {
            padding = 4 - padding;
        }
        return padding;
    }

}
