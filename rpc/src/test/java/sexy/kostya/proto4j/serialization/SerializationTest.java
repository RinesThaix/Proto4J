package sexy.kostya.proto4j.serialization;

import org.junit.Assert;
import org.junit.Test;
import sexy.kostya.proto4j.rpc.BufferSerializer;
import sexy.kostya.proto4j.serialization.exception.Proto4jSerializationException;
import sexy.kostya.proto4j.transport.buffer.Buffer;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Created by k.shandurenko on 30.09.2020
 */
public class SerializationTest {

    @Test
    public void testOKs() {
        ok(RawSerializable.class, new RawSerializable(1, null, "123"));
        ok(RawSerializable.class, new RawSerializable(1026, "3212", "123"));
    }

    @Test
    public void testExceptions() {
        exception(Serializer.class);
        exception(StringBuilder.class);
    }

    private <S> void exception(Class<S> clazz) {
        Serializer<Buffer> serializer = BufferSerializer.getInstance();
        try {
            serializer.getReader(clazz);
            serializer.getWriter(clazz);
        } catch (Proto4jSerializationException ignored) {
            return; // ok
        }
        Assert.fail();
    }

    private <S extends Proto4jSerializable> void ok(Class<S> clazz, S serializable) {
        Serializer<Buffer>    serializer = BufferSerializer.getInstance();
        Function<Buffer, S>   reader     = serializer.getReader(clazz);
        BiConsumer<Buffer, S> writer     = serializer.getWriter(clazz);
        try (Buffer buffer = Buffer.newBuffer()) {
            writer.accept(buffer, serializable);
            S newSerializable = reader.apply(buffer);
            Assert.assertEquals(serializable, newSerializable);
        }
    }

}
