import sexy.kostya.proto4j.rpc.serialization.Proto4jSerializable;
import sexy.kostya.proto4j.transport.buffer.Buffer;

import java.util.Objects;

/**
 * Created by k.shandurenko on 30.09.2020
 */
public class RawSerializable implements Proto4jSerializable {

    private int i;
    private String nullable;
    private String notNullable;

    public RawSerializable() {}

    public RawSerializable(int i, String nullable, String notNullable) {
        this.i = i;
        this.nullable = nullable;
        this.notNullable = notNullable;
    }

    @Override
    public void write(Buffer buffer) {
        buffer.writeInt(this.i);
        buffer.writeStringMaybe(this.nullable);
        buffer.writeString(this.notNullable);
    }

    @Override
    public void read(Buffer buffer) {
        this.i = buffer.readInt();
        this.nullable = buffer.readStringMaybe();
        this.notNullable = buffer.readString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        RawSerializable that = (RawSerializable) o;
        return i == that.i &&
                Objects.equals(nullable, that.nullable) &&
                Objects.equals(notNullable, that.notNullable);
    }

    @Override
    public int hashCode() {
        return Objects.hash(i, nullable, notNullable);
    }
}
