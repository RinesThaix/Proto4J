package sexy.kostya.proto4j.serialization;

import java.lang.reflect.Type;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Created by k.shandurenko on 03.10.2020
 */
public interface Serializer<B> {

    <T> BiConsumer<B, T> getWriter(Type type);

    <T> Function<B, T> getReader(Type type);

}
