package sexy.kostya.proto4j.serialization;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Created by k.shandurenko on 03.10.2020
 */
public class BaseTypeSerializable<B, T> {

    private final BiConsumer<B, T> writer;
    private final Function<B, T>   reader;

    public BaseTypeSerializable(BiConsumer<B, T> writer, Function<B, T> reader) {
        this.writer = writer;
        this.reader = reader;
    }

    public BiConsumer<B, T> getWriter() {
        return writer;
    }

    public Function<B, T> getReader() {
        return reader;
    }

}
