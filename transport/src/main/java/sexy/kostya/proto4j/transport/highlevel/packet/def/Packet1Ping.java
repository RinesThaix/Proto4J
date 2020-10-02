package sexy.kostya.proto4j.transport.highlevel.packet.def;

import sexy.kostya.proto4j.transport.buffer.Buffer;
import sexy.kostya.proto4j.transport.highlevel.packet.EnumeratedProto4jPacket;

/**
 * Created by k.shandurenko on 01.10.2020
 */
public class Packet1Ping extends EnumeratedProto4jPacket {

    public final static int ID = -1;

    @Override
    public int getID() {
        return ID;
    }

    @Override
    public void write(Buffer buffer) {

    }

    @Override
    public void read(Buffer buffer) {

    }

}
