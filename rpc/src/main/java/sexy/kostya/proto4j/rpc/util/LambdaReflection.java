package sexy.kostya.proto4j.rpc.util;

import java.lang.invoke.*;
import java.lang.reflect.Field;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Created by k.shandurenko on 03.10.2020
 */
@SuppressWarnings({"DuplicateThrows", "unchecked"})
public class LambdaReflection {

    private final static MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    public static BiConsumer<Object, Object> setFieldValue(Field f) throws IllegalAccessException, LambdaConversionException, Throwable {
        f.setAccessible(true);
        MethodHandle handle = LOOKUP.unreflectSetter(f);
        MethodType   type   = handle.type();
        return (BiConsumer<Object, Object>) LambdaMetafactory.metafactory(
                LOOKUP,
                "accept",
                MethodType.methodType(BiConsumer.class, MethodHandle.class),
                type.generic().changeReturnType(void.class),
                MethodHandles.exactInvoker(type),
                type
        ).getTarget().invokeExact(handle);
    }

    public static Function<Object, Object> getFieldValue(Field f)
            throws LambdaConversionException, IllegalAccessException, Throwable {
        f.setAccessible(true);
        MethodHandle handle = LOOKUP.unreflectGetter(f);
        MethodType   type   = handle.type();
        return (Function<Object, Object>) LambdaMetafactory.metafactory(
                LOOKUP,
                "apply",
                MethodType.methodType(Function.class, MethodHandle.class),
                type.generic(),
                MethodHandles.exactInvoker(type),
                type
        ).getTarget().invokeExact(handle);
    }

    public static <T> Supplier<T> getConstructor(Class<T> clazz)
            throws NoSuchMethodException, IllegalAccessException, LambdaConversionException, Throwable {
        MethodHandle handle = LOOKUP.findConstructor(clazz, MethodType.methodType(void.class));
        return (Supplier<T>) LambdaMetafactory.metafactory(
                LOOKUP,
                "get",
                MethodType.methodType(Supplier.class),
                handle.type().generic(),
                handle,
                handle.type()
        ).getTarget().invokeExact();
    }

}
