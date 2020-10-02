package sexy.kostya.proto4j.transport.highlevel.packet;

import sexy.kostya.proto4j.transport.buffer.Buffer;

/**
 * Created by k.shandurenko on 01.10.2020
 */
public abstract class EnumeratedProto4jPacket {

    public abstract int getID();

    public abstract void write(Buffer buffer);

    public abstract void read(Buffer buffer);

    public void write0(Buffer buffer) {
        write(buffer);
    }

    public void read0(Buffer buffer) {
        read(buffer);
    }

}
