package sexy.kostya.proto4j.rpc.serialization;

import sexy.kostya.proto4j.exception.Proto4jSerializationException;
import sexy.kostya.proto4j.transport.buffer.Buffer;

import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
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
        addBaseType(byte.class, Byte[]::new, new BaseTypeSerializable<>(Buffer::writeByte, Buffer::readByte));
        addBaseType(boolean.class, Boolean[]::new, new BaseTypeSerializable<>(Buffer::writeBoolean, Buffer::readBoolean));
        addBaseType(short.class, Short[]::new, new BaseTypeSerializable<>(Buffer::writeShort, Buffer::readShort));
        addBaseType(char.class, Character[]::new, new BaseTypeSerializable<>((buf, val) -> buf.writeShort((short) (char) val), buf -> (char) buf.readShort()));
        addBaseType(int.class, Integer[]::new, new BaseTypeSerializable<>(Buffer::writeVarInt, Buffer::readVarInt));
        addBaseType(long.class, Long[]::new, new BaseTypeSerializable<>(Buffer::writeVarLong, Buffer::readVarLong));
        addBaseType(String.class, String[]::new, new BaseTypeSerializable<>(Buffer::writeString, Buffer::readString));
        addBaseType(UUID.class, UUID[]::new, new BaseTypeSerializable<>(Buffer::writeUUID, Buffer::readUUID));

        addBaseType(Byte.class, byte.class);
        addBaseType(Boolean.class, boolean.class);
        addBaseType(Short.class, short.class);
        addBaseType(Character.class, char.class);
        addBaseType(Integer.class, int.class);
        addBaseType(Long.class, long.class);
    }

    private static <T> void addBaseType(Class<T> clazz, Function<Integer, T[]> arrayGenerator, BaseTypeSerializable<T> serializable) {
        BASE_TYPES.put(clazz, serializable);

        BaseTypeSerializable<T[]> arraySerializable = new BaseTypeSerializable<>((buf, val) -> {
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
        });
        BASE_TYPES.put(getArrayType(clazz), arraySerializable);
    }

    private static <T> void addBaseType(Class<T> clazz, Class<?> alias) {
        BASE_TYPES.put(clazz, BASE_TYPES.get(alias));
        BASE_TYPES.put(getArrayType(clazz), BASE_TYPES.get(getArrayType(alias)));
    }

    private static Class<?> getArrayType(Class<?> type) {
        return Array.newInstance(type, 0).getClass();
    }

    @SuppressWarnings("unchecked")
    public static <T> BiConsumer<Buffer, T> getWriter(Class<T> clazz) {
        BaseTypeSerializable<?> serializable = BASE_TYPES.get(clazz);
        if (serializable != null) {
            return ((BaseTypeSerializable<T>) serializable).writer;
        }
        if (Proto4jSerializable.class.isAssignableFrom(clazz)) {
            return (buffer, val) -> ((Proto4jSerializable) val).write(buffer);
        }
        if (Collection.class.isAssignableFrom(clazz)) {
            Class                      elType   = clazz.getTypeParameters()[0].getGenericDeclaration();
            BiConsumer<Buffer, Object> elWriter = getWriter(elType);
            return (buffer, val) -> buffer.writeCollection((Collection) val, el -> elWriter.accept(buffer, el));
        }
        if (Map.class.isAssignableFrom(clazz)) {
            Class                      keyType     = clazz.getTypeParameters()[0].getGenericDeclaration();
            Class                      valueType   = clazz.getTypeParameters()[1].getGenericDeclaration();
            BiConsumer<Buffer, Object> keyWriter   = getWriter(keyType);
            BiConsumer<Buffer, Object> valueWriter = getWriter(valueType);
            return (buffer, val) -> buffer.writeMap((Map) val, k -> keyWriter.accept(buffer, k), v -> valueWriter.accept(buffer, v));
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
        throw new Proto4jSerializationException(clazz.getSimpleName() + " can't be serialized: unknown type");
    }

    @SuppressWarnings("unchecked")
    public static <T> Function<Buffer, T> getReader(Class<T> clazz) {
        BaseTypeSerializable<?> serializable = BASE_TYPES.get(clazz);
        if (serializable != null) {
            return ((BaseTypeSerializable<T>) serializable).reader;
        }
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
        if (List.class.isAssignableFrom(clazz)) {
            Class                    elType   = clazz.getTypeParameters()[0].getGenericDeclaration();
            Function<Buffer, Object> elReader = getReader(elType);
            return buffer -> (T) buffer.readArrayList(() -> elReader.apply(buffer));
        }
        if (Set.class.isAssignableFrom(clazz)) {
            Class                    elType   = clazz.getTypeParameters()[0].getGenericDeclaration();
            Function<Buffer, Object> elReader = getReader(elType);
            return buffer -> (T) buffer.readHashSet(() -> elReader.apply(buffer));
        }
        if (Map.class.isAssignableFrom(clazz)) {
            Class                    keyType     = clazz.getTypeParameters()[0].getGenericDeclaration();
            Class                    valueType   = clazz.getTypeParameters()[1].getGenericDeclaration();
            Function<Buffer, Object> keyReader   = getReader(keyType);
            Function<Buffer, Object> valueReader = getReader(valueType);
            return buffer -> (T) buffer.readHashMap(() -> keyReader.apply(buffer), () -> valueReader.apply(buffer));
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
        throw new Proto4jSerializationException(clazz.getSimpleName() + " can't be deserialized: unknown type");
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
