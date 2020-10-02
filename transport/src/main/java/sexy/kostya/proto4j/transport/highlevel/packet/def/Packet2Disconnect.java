package sexy.kostya.proto4j.transport.highlevel.packet.def;

import sexy.kostya.proto4j.transport.buffer.Buffer;
import sexy.kostya.proto4j.transport.highlevel.packet.EnumeratedProto4jPacket;

/**
 * Created by k.shandurenko on 01.10.2020
 */
public class Packet2Disconnect extends EnumeratedProto4jPacket {

    public final static int ID = -2;

    private String reason;

    public Packet2Disconnect() {

    }

    public Packet2Disconnect(String reason) {
        this.reason = reason;
    }

    public String getReason() {
        return this.reason;
    }

    @Override
    public int getID() {
        return ID;
    }

    @Override
    public void write(Buffer buffer) {
        buffer.writeStringMaybe(this.reason);
    }

    @Override
    public void read(Buffer buffer) {
        this.reason = buffer.readStringMaybe();
    }

}
