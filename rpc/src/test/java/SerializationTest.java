import org.junit.Assert;
import org.junit.Test;
import sexy.kostya.proto4j.transport.buffer.Buffer;
import sexy.kostya.proto4j.exception.Proto4jSerializationException;
import sexy.kostya.proto4j.transport.packet.serialization.Proto4jSerializable;
import sexy.kostya.proto4j.transport.packet.serialization.SerializationMaster;

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
        exception(SerializationMaster.class);
        exception(StringBuilder.class);
    }

    private <S> void exception(Class<S> clazz) {
        try {
            SerializationMaster.getReader(clazz);
            SerializationMaster.getWriter(clazz);
        } catch (Proto4jSerializationException ignored) {
            return; // ok
        }
        Assert.fail();
    }

    private <S extends Proto4jSerializable> void ok(Class<S> clazz, S serializable) {
        Function<Buffer, S> reader = SerializationMaster.getReader(clazz);
        BiConsumer<Buffer, S> writer = SerializationMaster.getWriter(clazz);
        try (Buffer buffer = Buffer.newBuffer()) {
            writer.accept(buffer, serializable);
            S newSerializable = reader.apply(buffer);
            Assert.assertEquals(serializable, newSerializable);
        }
    }

}
