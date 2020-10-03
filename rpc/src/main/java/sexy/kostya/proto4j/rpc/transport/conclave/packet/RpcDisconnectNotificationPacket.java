package sexy.kostya.proto4j.rpc.transport.conclave.packet;

import sexy.kostya.proto4j.transport.buffer.Buffer;
import sexy.kostya.proto4j.transport.highlevel.packet.EnumeratedProto4jPacket;

/**
 * Created by k.shandurenko on 03.10.2020
 */
public class RpcDisconnectNotificationPacket extends EnumeratedProto4jPacket {

    private int channelID;

    public RpcDisconnectNotificationPacket() {
    }

    public RpcDisconnectNotificationPacket(int channelID) {
        this.channelID = channelID;
    }

    public int getChannelID() {
        return channelID;
    }

    @Override
    public int getID() {
        return 6;
    }

    @Override
    public void write(Buffer buffer) {
        buffer.writeInt(this.channelID);
    }

    @Override
    public void read(Buffer buffer) {
        this.channelID = buffer.readInt();
    }
}
