package sexy.kostya.proto4j.serialization;

import sexy.kostya.proto4j.serialization.annotation.AutoSerializable;
import sexy.kostya.proto4j.serialization.annotation.Transient;
import sexy.kostya.proto4j.serialization.exception.Proto4jSerializationException;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Created by k.shandurenko on 03.10.2020
 */
@SuppressWarnings("unchecked")
public abstract class BaseSerializer<B> implements Serializer<B> {

    private final Map<Type, BaseTypeSerializable<B, ?>> baseTypes = new HashMap<>();

    public BaseSerializer() {
        registerBaseTypes();
        validateBaseTypes();
    }

    protected abstract void registerBaseTypes();

    private void validateBaseTypes() {
        List<Type> types = Arrays.asList(
                void.class, Void.class,
                byte.class, Byte.class, byte[].class,
                boolean.class, Boolean.class, boolean[].class,
                char.class, Character.class, char[].class,
                short.class, Short.class, short[].class,
                int.class, Integer.class, int[].class,
                long.class, Long.class, long[].class,
                String.class, String[].class
        );
        types.forEach(type -> {
            if (!this.baseTypes.containsKey(type)) {
                throw new IllegalStateException("Base type " + type.getTypeName() + " is not registered");
            }
        });
    }

    protected <T> void registerBaseType(Class<T> clazz, BaseTypeSerializable<B, T> serializable) {
        registerBaseTypeUnsafe(clazz, serializable);
    }

    protected void registerBaseTypeUnsafe(Class<?> clazz, BaseTypeSerializable<B, ?> serializable) {
        this.baseTypes.put(clazz, serializable);
    }

    protected <T> BaseTypeSerializable<B, T> getBaseTypeSerializable(Class<T> clazz) {
        return (BaseTypeSerializable<B, T>) this.baseTypes.get(clazz);
    }

    protected static Class<?> getArrayType(Class<?> type) {
        return Array.newInstance(type, 0).getClass();
    }

    @Override
    public <T> BiConsumer<B, T> getWriter(Type type) {
        BaseTypeSerializable<B, ?> serializable = baseTypes.get(type);
        if (serializable != null) {
            return ((BaseTypeSerializable<B, T>) serializable).getWriter();
        }
        try {
            if (type instanceof Class) {
                Class<T> clazz = (Class<T>) type;
                if (Proto4jSerializable.class.isAssignableFrom(clazz)) {
                    return (buffer, val) -> ((Proto4jSerializable<B>) val).write(buffer);
                }
                if (clazz.isAnnotationPresent(AutoSerializable.class)) {
                    Class<? super T>              parent = clazz.getSuperclass();
                    BiConsumer<B, ? super T> parentWriter;
                    if (parent == Object.class) {
                        parentWriter = null;
                    } else {
                        if (!parent.isAnnotationPresent(AutoSerializable.class)) {
                            throw new Proto4jSerializationException(clazz.getSimpleName() + " can't be serialized: it's parent " + parent.getSimpleName() + " is not annotated with @AutoSerializable");
                        }
                        parentWriter = getWriter(parent);
                    }

                    Map<Field, Function<T, Object>>        getters = new HashMap<>();
                    Map<Field, BiConsumer<B, Object>> writers = new HashMap<>();
                    for (Field f : clazz.getDeclaredFields()) {
                        if (f.isSynthetic() || (f.getModifiers() & Modifier.STATIC) != 0) {
                            continue;
                        }
                        if (f.isAnnotationPresent(Transient.class)) {
                            continue;
                        }
                        Type fType = f.getGenericType();
                        getters.put(f, getFieldValue(clazz, f));
                        writers.put(f, getWriter(fType));
                    }
                    return (buffer, val) -> {
                        if (parentWriter != null) {
                            parentWriter.accept(buffer, val);
                        }
                        getters.forEach((f, getter) -> {
                            BiConsumer<B, Object> writer = writers.get(f);
                            writer.accept(buffer, getter.apply(val));
                        });
                    };
                }
                if (clazz.isArray()) {
                    Class                      elType   = clazz.getComponentType();
                    BiConsumer<B, Object> elWriter = getWriter(elType);
                    BiConsumer<B, Integer> intWriter = getWriter(int.class);
                    return (buffer, val) -> {
                        Object[] casted = (Object[]) val;
                        int      size   = casted.length;
                        intWriter.accept(buffer, size);
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

                        BiConsumer<B, Object> elWriter = getWriter(elType);
                        BiConsumer<B, Integer> intWriter = getWriter(int.class);
                        return (buffer, val) -> {
                            Collection col = (Collection) val;
                            intWriter.accept(buffer, col.size());
                            col.forEach(el -> elWriter.accept(buffer, el));
                        };
                    }
                    if (Map.class.isAssignableFrom(clazz)) {
                        Type keyType   = parameterizedType.getActualTypeArguments()[0];
                        Type valueType = parameterizedType.getActualTypeArguments()[1];

                        BiConsumer<B, Object> keyWriter   = getWriter(keyType);
                        BiConsumer<B, Object> valueWriter = getWriter(valueType);
                        BiConsumer<B, Integer> intWriter = getWriter(int.class);
                        return (buffer, val) -> {
                            Map map = (Map) val;
                            intWriter.accept(buffer, map.size());
                            map.forEach((key, value) -> {
                                keyWriter.accept(buffer, key);
                                valueWriter.accept(buffer, value);
                            });
                        };
                    }
                }
            }
        } catch (Throwable t) {
            throw new Proto4jSerializationException(type.getTypeName() + " can't be serialized", t);
        }
        throw new Proto4jSerializationException(type.getTypeName() + " can't be serialized: unknown type");
    }

    @Override
    public <T> Function<B, T> getReader(Type type) {
        BiFunction<B, T, T> internal = getInternalReader(type);
        return buffer -> internal.apply(buffer, null);
    }

    private <T> BiFunction<B, T, T> getInternalReader(Type type) {
        BaseTypeSerializable<B, ?> serializable = baseTypes.get(type);
        if (serializable != null) {
            return (buf, initial) -> ((BaseTypeSerializable<B, T>) serializable).getReader().apply(buf);
        }
        if (type instanceof Class) {
            Class<T> clazz = (Class<T>) type;
            if (Proto4jSerializable.class.isAssignableFrom(clazz)) {
                Supplier<T> constructor = getConstructor(clazz);
                return (buffer, initial) -> {
                    T value = constructor.get();
                    ((Proto4jSerializable) value).read(buffer);
                    return value;
                };
            }
            if (clazz.isAnnotationPresent(AutoSerializable.class)) {
                Class<? super T>                   parent = clazz.getSuperclass();
                BiFunction<B, T, ? extends T> parentReader;
                if (parent == Object.class) {
                    parentReader = null;
                } else {
                    if (!parent.isAnnotationPresent(AutoSerializable.class)) {
                        throw new Proto4jSerializationException(clazz.getSimpleName() + " can't be deserialized: it's parent " + parent.getSimpleName() + " is not annotated with @AutoSerializable");
                    }
                    parentReader = getInternalReader(parent);
                }

                Map<Field, BiConsumer<T, Object>>              setters     = new HashMap<>();
                Map<Field, BiFunction<B, Object, Object>> readers     = new HashMap<>();
                Supplier<T>                                    constructor = getConstructor(clazz);
                for (Field f : clazz.getDeclaredFields()) {
                    if (f.isSynthetic() || (f.getModifiers() & Modifier.STATIC) != 0) {
                        continue;
                    }
                    if (f.isAnnotationPresent(Transient.class)) {
                        continue;
                    }
                    Type fType = f.getGenericType();
                    setters.put(f, setFieldValue(clazz, f));
                    readers.put(f, getInternalReader(fType));
                }
                return (buffer, initial) -> {
                    T val = initial != null ? initial : constructor.get();
                    if (parentReader != null) {
                        parentReader.apply(buffer, val);
                    }
                    setters.forEach((f, setter) -> {
                        BiFunction<B, Object, Object> reader = readers.get(f);
                        setter.accept(val, reader.apply(buffer, null));
                    });
                    return val;
                };
            }
            if (clazz.isArray()) {
                Class                              elType   = clazz.getComponentType();
                BiFunction<B, Object, Object> elReader = getInternalReader(elType);
                BiFunction<B, Integer, Integer> intReader = getInternalReader(int.class);
                return (buffer, initial) -> {
                    int size = intReader.apply(buffer, null);
                    Object   array  = Array.newInstance(elType, size);
                    Object[] casted = (Object[]) array;
                    for (int i = 0; i < size; ++i) {
                        casted[i] = elReader.apply(buffer, null);
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

                    BiFunction<B, Object, Object> elReader = getInternalReader(elType);
                    BiFunction<B, Integer, Integer> intReader = getInternalReader(int.class);
                    return (buffer, initial) -> {
                        int size = intReader.apply(buffer, null);
                        List list = new ArrayList(size);
                        for (int i = 0; i < size; ++i) {
                            list.add(elReader.apply(buffer, null));
                        }
                        return (T) list;
                    };
                }
                if (Set.class.isAssignableFrom(clazz)) {
                    Type elType = parameterizedType.getActualTypeArguments()[0];

                    BiFunction<B, Object, Object> elReader = getInternalReader(elType);
                    BiFunction<B, Integer, Integer> intReader = getInternalReader(int.class);
                    return (buffer, initial) -> {
                        int size = intReader.apply(buffer, null);
                        Set set = new HashSet(size);
                        for (int i = 0; i < size; ++i) {
                            set.add(elReader.apply(buffer, null));
                        }
                        return (T) set;
                    };
                }
                if (Map.class.isAssignableFrom(clazz)) {
                    Type keyType   = parameterizedType.getActualTypeArguments()[0];
                    Type valueType = parameterizedType.getActualTypeArguments()[1];

                    BiFunction<B, Object, Object> keyReader   = getInternalReader(keyType);
                    BiFunction<B, Object, Object> valueReader = getInternalReader(valueType);
                    BiFunction<B, Integer, Integer> intReader = getInternalReader(int.class);
                    return (buffer, initial) -> {
                        int size = intReader.apply(buffer, null);
                        Map map = new HashMap(size);
                        for (int i = 0; i < size; ++i) {
                            map.put(keyReader.apply(buffer, null), valueReader.apply(buffer, null));
                        }
                        return (T) map;
                    };
                }
            }
        }
        throw new Proto4jSerializationException(type.getTypeName() + " can't be deserialized: unknown type");
    }

    private static <T> BiConsumer<T, Object> setFieldValue(Class<T> clazz, Field f) {
        try {
            return (BiConsumer<T, Object>) LambdaReflection.setFieldValue(f);
        } catch (Throwable throwable) {
            throw new Proto4jSerializationException(clazz.getSimpleName() + " can't be deserialized", throwable);
        }
    }

    private static <T> Function<T, Object> getFieldValue(Class<T> clazz, Field f) {
        try {
            return (Function<T, Object>) LambdaReflection.getFieldValue(f);
        } catch (Throwable throwable) {
            throw new Proto4jSerializationException(clazz.getSimpleName() + " can't be serialized", throwable);
        }
    }

    private static <T> Supplier<T> getConstructor(Class<T> clazz) {
        try {
            return LambdaReflection.getConstructor(clazz);
        } catch (NoSuchMethodException e) {
            throw new Proto4jSerializationException(clazz.getSimpleName() + " can't be deserialized: it doesn't have default constructor");
        } catch (IllegalAccessException e) {
            throw new Proto4jSerializationException(clazz.getSimpleName() + " can't be deserialized: it's default constructor is not public");
        } catch (Throwable throwable) {
            throw new Proto4jSerializationException(clazz.getSimpleName() + " can't be deserialized", throwable);
        }
    }

}
