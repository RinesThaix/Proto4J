package sexy.kostya.proto4j.rpc.transport.conclave.packet;

import sexy.kostya.proto4j.transport.buffer.Buffer;
import sexy.kostya.proto4j.transport.highlevel.packet.EnumeratedProto4jPacket;

/**
 * Created by k.shandurenko on 03.10.2020
 */
public class RpcServiceNotificationPacket extends EnumeratedProto4jPacket {

    private int channelID;
    private int serviceID;

    public RpcServiceNotificationPacket() {
    }

    public RpcServiceNotificationPacket(int channelID, int serviceID) {
        this.channelID = channelID;
        this.serviceID = serviceID;
    }

    public int getChannelID() {
        return channelID;
    }

    public int getServiceID() {
        return serviceID;
    }

    @Override
    public int getID() {
        return 5;
    }

    @Override
    public void write(Buffer buffer) {
        buffer.writeInt(this.channelID);
        buffer.writeInt(this.serviceID);
    }

    @Override
    public void read(Buffer buffer) {
        this.channelID = buffer.readInt();
        this.serviceID = buffer.readInt();
    }
}
