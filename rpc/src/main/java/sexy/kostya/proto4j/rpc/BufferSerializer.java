package sexy.kostya.proto4j.rpc;

import sexy.kostya.proto4j.serialization.BaseSerializer;
import sexy.kostya.proto4j.serialization.BaseTypeSerializable;
import sexy.kostya.proto4j.serialization.Serializer;
import sexy.kostya.proto4j.serialization.exception.Proto4jSerializationException;
import sexy.kostya.proto4j.transport.buffer.Buffer;

import java.util.UUID;
import java.util.function.Function;

/**
 * Created by k.shandurenko on 03.10.2020
 */
public class BufferSerializer extends BaseSerializer<Buffer> {

    private final static BufferSerializer INSTANCE = new BufferSerializer();

    public static Serializer<Buffer> getInstance() {
        return INSTANCE;
    }

    @Override
    protected void registerBaseTypes() {
        addPrimitiveType(void.class, new BaseTypeSerializable<>((buf, val) -> {
        }, buf -> null));
        addPrimitiveType(byte.class, new BaseTypeSerializable<>(Buffer::writeByte, Buffer::readByte));
        addPrimitiveType(boolean.class, new BaseTypeSerializable<>(Buffer::writeBoolean, Buffer::readBoolean));
        addPrimitiveType(short.class, new BaseTypeSerializable<>(Buffer::writeShort, Buffer::readShort));
        addPrimitiveType(char.class, new BaseTypeSerializable<>((buf, val) -> buf.writeShort((short) (char) val), buf -> (char) buf.readShort()));
        addPrimitiveType(int.class, new BaseTypeSerializable<>(Buffer::writeVarInt, Buffer::readVarInt));
        addPrimitiveType(long.class, new BaseTypeSerializable<>(Buffer::writeVarLong, Buffer::readVarLong));
        addBaseType(String.class, String[]::new, new BaseTypeSerializable<>(Buffer::writeString, Buffer::readString));
        addBaseType(UUID.class, UUID[]::new, new BaseTypeSerializable<>(Buffer::writeUUID, Buffer::readUUID));

        addPrimitiveWrapper(Void.class, void.class, null);
        addPrimitiveWrapper(Byte.class, byte.class, Byte[]::new);
        addPrimitiveWrapper(Boolean.class, boolean.class, Boolean[]::new);
        addPrimitiveWrapper(Short.class, short.class, Short[]::new);
        addPrimitiveWrapper(Character.class, char.class, Character[]::new);
        addPrimitiveWrapper(Integer.class, int.class, Integer[]::new);
        addPrimitiveWrapper(Long.class, long.class, Long[]::new);
    }

    private <T> void addPrimitiveType(Class<T> clazz, BaseTypeSerializable<Buffer, T> serializable) {
        registerBaseType(clazz, serializable);
        if (clazz != void.class) {
            registerBaseTypeUnsafe(getArrayType(clazz), getPrimitiveArraySerializable(clazz, serializable));
        }
    }

    private <T> void addBaseType(Class<T> clazz, Function<Integer, T[]> arrayGenerator, BaseTypeSerializable<Buffer, T> serializable) {
        registerBaseType(clazz, serializable);
        registerBaseTypeUnsafe(getArrayType(clazz), new BaseTypeSerializable<>((buf, val) -> {
            buf.writeVarInt(val.length);
            for (int i = 0; i < val.length; ++i) {
                serializable.getWriter().accept(buf, val[i]);
            }
        }, buf -> {
            T[] array = arrayGenerator.apply(buf.readVarInt());
            for (int i = 0; i < array.length; ++i) {
                array[i] = serializable.getReader().apply(buf);
            }
            return array;
        }));
    }

    @SuppressWarnings("unchecked")
    private <T, A> void addPrimitiveWrapper(Class<T> clazz, Class<A> alias, Function<Integer, T[]> arrayGenerator) {
        BaseTypeSerializable<Buffer, A> serializable = getBaseTypeSerializable(alias);
        registerBaseTypeUnsafe(clazz, serializable);
        if (arrayGenerator != null) {
            BaseTypeSerializable<Buffer, T[]> arraySerializable = new BaseTypeSerializable<>((buf, val) -> {
                buf.writeVarInt(val.length);
                for (int i = 0; i < val.length; ++i) {
                    serializable.getWriter().accept(buf, (A) val[i]);
                }
            }, buf -> {
                T[] array = arrayGenerator.apply(buf.readVarInt());
                for (int i = 0; i < array.length; ++i) {
                    array[i] = (T) serializable.getReader().apply(buf);
                }
                return array;
            });
            registerBaseTypeUnsafe(getArrayType(clazz), arraySerializable);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> BaseTypeSerializable<Buffer, ?> getPrimitiveArraySerializable(Class<?> clazz, BaseTypeSerializable<Buffer, T> serializable) {
        if (clazz == byte.class) {
            return new BaseTypeSerializable<>((buf, val) -> {
                buf.writeVarInt(val.length);
                for (byte aVal : val) {
                    serializable.getWriter().accept(buf, (T) (Byte) aVal);
                }
            }, buf -> {
                byte[] array = new byte[buf.readVarInt()];
                for (int i = 0; i < array.length; ++i) {
                    array[i] = (byte) (Byte) serializable.getReader().apply(buf);
                }
                return array;
            });
        }
        if (clazz == boolean.class) {
            return new BaseTypeSerializable<>((buf, val) -> {
                buf.writeVarInt(val.length);
                for (boolean aVal : val) {
                    serializable.getWriter().accept(buf, (T) (Boolean) aVal);
                }
            }, buf -> {
                boolean[] array = new boolean[buf.readVarInt()];
                for (int i = 0; i < array.length; ++i) {
                    array[i] = (boolean) (Boolean) serializable.getReader().apply(buf);
                }
                return array;
            });
        }
        if (clazz == short.class) {
            return new BaseTypeSerializable<>((buf, val) -> {
                buf.writeVarInt(val.length);
                for (short aVal : val) {
                    serializable.getWriter().accept(buf, (T) (Short) aVal);
                }
            }, buf -> {
                short[] array = new short[buf.readVarInt()];
                for (int i = 0; i < array.length; ++i) {
                    array[i] = (short) (Short) serializable.getReader().apply(buf);
                }
                return array;
            });
        }
        if (clazz == char.class) {
            return new BaseTypeSerializable<>((buf, val) -> {
                buf.writeVarInt(val.length);
                for (char aVal : val) {
                    serializable.getWriter().accept(buf, (T) (Character) aVal);
                }
            }, buf -> {
                char[] array = new char[buf.readVarInt()];
                for (int i = 0; i < array.length; ++i) {
                    array[i] = (char) (Character) serializable.getReader().apply(buf);
                }
                return array;
            });
        }
        if (clazz == int.class) {
            return new BaseTypeSerializable<>((buf, val) -> {
                buf.writeVarInt(val.length);
                for (int aVal : val) {
                    serializable.getWriter().accept(buf, (T) (Integer) aVal);
                }
            }, buf -> {
                int[] array = new int[buf.readVarInt()];
                for (int i = 0; i < array.length; ++i) {
                    array[i] = (int) (Integer) serializable.getReader().apply(buf);
                }
                return array;
            });
        }
        if (clazz == long.class) {
            return new BaseTypeSerializable<>((buf, val) -> {
                buf.writeVarInt(val.length);
                for (long aVal : val) {
                    serializable.getWriter().accept(buf, (T) (Long) aVal);
                }
            }, buf -> {
                long[] array = new long[buf.readVarInt()];
                for (int i = 0; i < array.length; ++i) {
                    array[i] = (long) (Long) serializable.getReader().apply(buf);
                }
                return array;
            });
        }
        throw new Proto4jSerializationException("Could not construct serializer for array of primitive " + clazz.getSimpleName());
    }

}
