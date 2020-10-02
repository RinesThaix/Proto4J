package sexy.kostya.proto4j.rpc.serialization;

import sexy.kostya.proto4j.exception.Proto4jSerializationException;
import sexy.kostya.proto4j.transport.buffer.Buffer;

import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Created by k.shandurenko on 30.09.2020
 */
public class SerializationMaster {

    private final static Map<Class<?>, BaseTypeSerializable<?>> BASE_TYPES = new HashMap<>();

    static {
        addPrimitiveType(void.class, new BaseTypeSerializable<>((buf, val) -> {}, buf -> null));
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

    private static <T> void addPrimitiveType(Class<T> clazz, BaseTypeSerializable<T> serializable) {
        BASE_TYPES.put(clazz, serializable);
        if (clazz != void.class) {
            BASE_TYPES.put(getArrayType(clazz), getPrimitiveArraySerializable(clazz, serializable));
        }
    }

    private static <T> void addBaseType(Class<T> clazz, Function<Integer, T[]> arrayGenerator, BaseTypeSerializable<T> serializable) {
        BASE_TYPES.put(clazz, serializable);
        BASE_TYPES.put(getArrayType(clazz), new BaseTypeSerializable<>((buf, val) -> {
            buf.writeVarInt(val.length);
            for (int i = 0; i < val.length; ++i) {
                serializable.writer.accept(buf, val[i]);
            }
        }, buf -> {
            T[] array = arrayGenerator.apply(buf.readVarInt());
            for (int i = 0; i < array.length; ++i) {
                array[i] = serializable.reader.apply(buf);
            }
            return array;
        }));
    }

    @SuppressWarnings("unchecked")
    private static <T, A> void addPrimitiveWrapper(Class<T> clazz, Class<A> alias, Function<Integer, T[]> arrayGenerator) {
        BaseTypeSerializable<A> serializable = (BaseTypeSerializable<A>) BASE_TYPES.get(alias);
        BASE_TYPES.put(clazz, serializable);
        if (arrayGenerator != null) {
            BaseTypeSerializable<T[]> arraySerializable = new BaseTypeSerializable<>((buf, val) -> {
                buf.writeVarInt(val.length);
                for (int i = 0; i < val.length; ++i) {
                    serializable.writer.accept(buf, (A) val[i]);
                }
            }, buf -> {
                T[] array = arrayGenerator.apply(buf.readVarInt());
                for (int i = 0; i < array.length; ++i) {
                    array[i] = (T) serializable.reader.apply(buf);
                }
                return array;
            });
            BASE_TYPES.put(getArrayType(clazz), arraySerializable);
        }
    }

    private static Class<?> getArrayType(Class<?> type) {
        return Array.newInstance(type, 0).getClass();
    }

    @SuppressWarnings("unchecked")
    public static <T> BiConsumer<Buffer, T> getWriter(Type type) {
        BaseTypeSerializable<?> serializable = BASE_TYPES.get(type);
        if (serializable != null) {
            return ((BaseTypeSerializable<T>) serializable).writer;
        }
        if (type instanceof Class) {
            Class<T>                clazz        = (Class<T>) type;
            if (Proto4jSerializable.class.isAssignableFrom(clazz)) {
                return (buffer, val) -> ((Proto4jSerializable) val).write(buffer);
            }
            if (clazz.isArray()) {
                Class                      elType   = clazz.getComponentType();
                BiConsumer<Buffer, Object> elWriter = getWriter(elType);
                return (buffer, val) -> {
                    Object[] casted = (Object[]) val;
                    int      size   = casted.length;
                    buffer.writeVarInt(size);
                    for (int i = 0; i < size; ++i) {
                        elWriter.accept(buffer, casted[i]);
                    }
                };
            }
        }
        if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            if (parameterizedType.getRawType() instanceof Class) {
                Class clazz = (Class) parameterizedType.getRawType();
                if (Collection.class.isAssignableFrom(clazz)) {
                    Type elType = parameterizedType.getActualTypeArguments()[0];

                    BiConsumer<Buffer, Object> elWriter = getWriter(elType);
                    return (buffer, val) -> buffer.writeCollection((Collection) val, el -> elWriter.accept(buffer, el));
                }
                if (Map.class.isAssignableFrom(clazz)) {
                    Type keyType   = parameterizedType.getActualTypeArguments()[0];
                    Type valueType = parameterizedType.getActualTypeArguments()[1];

                    BiConsumer<Buffer, Object> keyWriter   = getWriter(keyType);
                    BiConsumer<Buffer, Object> valueWriter = getWriter(valueType);
                    return (buffer, val) -> buffer.writeMap((Map) val, k -> keyWriter.accept(buffer, k), v -> valueWriter.accept(buffer, v));
                }
            }
        }
        throw new Proto4jSerializationException(type.getTypeName() + " can't be serialized: unknown type");
    }

    @SuppressWarnings("unchecked")
    public static <T> Function<Buffer, T> getReader(Type type) {
        BaseTypeSerializable<?> serializable = BASE_TYPES.get(type);
        if (serializable != null) {
            return ((BaseTypeSerializable<T>) serializable).reader;
        }
        if (type instanceof Class) {
            Class<T>                clazz        = (Class<T>) type;
            if (Proto4jSerializable.class.isAssignableFrom(clazz)) {
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                try {
                    MethodHandle handle = lookup.findConstructor(clazz, MethodType.methodType(void.class));
                    Supplier<T> constructor = (Supplier<T>) LambdaMetafactory.metafactory(
                            lookup,
                            "get",
                            MethodType.methodType(Supplier.class),
                            handle.type().generic(),
                            handle,
                            handle.type()
                    ).getTarget().invokeExact();
                    return buffer -> {
                        T value = constructor.get();
                        ((Proto4jSerializable) value).read(buffer);
                        return value;
                    };
                } catch (NoSuchMethodException e) {
                    throw new Proto4jSerializationException(clazz.getSimpleName() + " can't be deserialized: it doesn't have default constructor");
                } catch (IllegalAccessException e) {
                    throw new Proto4jSerializationException(clazz.getSimpleName() + " can't be deserialized: it's default constructor is not public");
                } catch (Throwable throwable) {
                    throw new Proto4jSerializationException(clazz.getSimpleName() + " can't be deserialized", throwable);
                }
            }
            if (clazz.isArray()) {
                Class                    elType   = clazz.getComponentType();
                Function<Buffer, Object> elReader = getReader(elType);
                return buffer -> {
                    int      size   = buffer.readVarInt();
                    Object   array  = Array.newInstance(elType, size);
                    Object[] casted = (Object[]) array;
                    for (int i = 0; i < size; ++i) {
                        casted[i] = elReader.apply(buffer);
                    }
                    return (T) array;
                };
            }
        }
        if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            if (parameterizedType.getRawType() instanceof Class) {
                Class clazz = (Class) parameterizedType.getRawType();
                if (List.class.isAssignableFrom(clazz)) {
                    Type elType = parameterizedType.getActualTypeArguments()[0];

                    Function<Buffer, Object> elReader = getReader(elType);
                    return buffer -> (T) buffer.readArrayList(() -> elReader.apply(buffer));
                }
                if (Set.class.isAssignableFrom(clazz)) {
                    Type elType = parameterizedType.getActualTypeArguments()[0];

                    Function<Buffer, Object> elReader = getReader(elType);
                    return buffer -> (T) buffer.readHashSet(() -> elReader.apply(buffer));
                }
                if (Map.class.isAssignableFrom(clazz)) {
                    Type keyType   = parameterizedType.getActualTypeArguments()[0];
                    Type valueType = parameterizedType.getActualTypeArguments()[1];

                    Function<Buffer, Object> keyReader   = getReader(keyType);
                    Function<Buffer, Object> valueReader = getReader(valueType);
                    return buffer -> (T) buffer.readHashMap(() -> keyReader.apply(buffer), () -> valueReader.apply(buffer));
                }
            }
        }
        throw new Proto4jSerializationException(type.getTypeName() + " can't be deserialized: unknown type");
    }

    @SuppressWarnings("unchecked")
    private static <T> BaseTypeSerializable getPrimitiveArraySerializable(Class<?> clazz, BaseTypeSerializable<T> serializable) {
        if (clazz == byte.class) {
            return new BaseTypeSerializable<>((buf, val) -> {
                buf.writeVarInt(val.length);
                for (byte aVal : val) {
                    serializable.writer.accept(buf, (T) (Byte) aVal);
                }
            }, buf -> {
                byte[] array = new byte[buf.readVarInt()];
                for (int i = 0; i < array.length; ++i) {
                    array[i] = (byte) (Byte) serializable.reader.apply(buf);
                }
                return array;
            });
        }
        if (clazz == boolean.class) {
            return new BaseTypeSerializable<>((buf, val) -> {
                buf.writeVarInt(val.length);
                for (boolean aVal : val) {
                    serializable.writer.accept(buf, (T) (Boolean) aVal);
                }
            }, buf -> {
                boolean[] array = new boolean[buf.readVarInt()];
                for (int i = 0; i < array.length; ++i) {
                    array[i] = (boolean) (Boolean) serializable.reader.apply(buf);
                }
                return array;
            });
        }
        if (clazz == short.class) {
            return new BaseTypeSerializable<>((buf, val) -> {
                buf.writeVarInt(val.length);
                for (short aVal : val) {
                    serializable.writer.accept(buf, (T) (Short) aVal);
                }
            }, buf -> {
                short[] array = new short[buf.readVarInt()];
                for (int i = 0; i < array.length; ++i) {
                    array[i] = (short) (Short) serializable.reader.apply(buf);
                }
                return array;
            });
        }
        if (clazz == char.class) {
            return new BaseTypeSerializable<>((buf, val) -> {
                buf.writeVarInt(val.length);
                for (char aVal : val) {
                    serializable.writer.accept(buf, (T) (Character) aVal);
                }
            }, buf -> {
                char[] array = new char[buf.readVarInt()];
                for (int i = 0; i < array.length; ++i) {
                    array[i] = (char) (Character) serializable.reader.apply(buf);
                }
                return array;
            });
        }
        if (clazz == int.class) {
            return new BaseTypeSerializable<>((buf, val) -> {
                buf.writeVarInt(val.length);
                for (int aVal : val) {
                    serializable.writer.accept(buf, (T) (Integer) aVal);
                }
            }, buf -> {
                int[] array = new int[buf.readVarInt()];
                for (int i = 0; i < array.length; ++i) {
                    array[i] = (int) (Integer) serializable.reader.apply(buf);
                }
                return array;
            });
        }
        if (clazz == long.class) {
            return new BaseTypeSerializable<>((buf, val) -> {
                buf.writeVarInt(val.length);
                for (long aVal : val) {
                    serializable.writer.accept(buf, (T) (Long) aVal);
                }
            }, buf -> {
                long[] array = new long[buf.readVarInt()];
                for (int i = 0; i < array.length; ++i) {
                    array[i] = (long) (Long) serializable.reader.apply(buf);
                }
                return array;
            });
        }
        throw new Proto4jSerializationException("Could not construct serializer for array of primitive " + clazz.getSimpleName());
    }

    private static class BaseTypeSerializable<T> {

        private final BiConsumer<Buffer, T> writer;
        private final Function<Buffer, T>   reader;

        public BaseTypeSerializable(BiConsumer<Buffer, T> writer, Function<Buffer, T> reader) {
            this.writer = writer;
            this.reader = reader;
        }

    }

}
