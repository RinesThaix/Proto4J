package sexy.kostya.proto4j.serialization;

import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Type;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Created by k.shandurenko on 03.10.2020
 */
public interface Serializer<B> {

    <T> BiConsumer<B, T> getWriter(AnnotatedType type);

    default <T> BiConsumer<B, T> getWriter(Type type, boolean nullable) {
        return getWriter(new MockedAnnotatedType(type, nullable));
    }

    <T> Function<B, T> getReader(AnnotatedType type);

    default <T> Function<B, T> getReader(Type type, boolean nullable) {
        return getReader(new MockedAnnotatedType(type, nullable));
    }

}
