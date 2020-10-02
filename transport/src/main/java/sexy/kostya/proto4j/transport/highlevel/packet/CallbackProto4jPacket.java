package sexy.kostya.proto4j.transport.highlevel.packet;

import sexy.kostya.proto4j.transport.buffer.Buffer;
import sexy.kostya.proto4j.transport.highlevel.HighChannel;

/**
 * Created by k.shandurenko on 01.10.2020
 */
public abstract class CallbackProto4jPacket extends EnumeratedProto4jPacket {

    private short callbackID;

    public short getCallbackID() {
        return this.callbackID;
    }

    public void setCallbackID(short callbackID) {
        this.callbackID = callbackID;
    }

    @Override
    public void write0(Buffer buffer) {
        buffer.writeShort(this.callbackID);
        super.write0(buffer);
    }

    @Override
    public void read0(Buffer buffer) {
        this.callbackID = buffer.readShort();
        super.read0(buffer);
    }

    public void respond(HighChannel channel, CallbackProto4jPacket packet) {
        packet.setCallbackID((short)-this.callbackID);
        channel.send(packet);
    }

}
