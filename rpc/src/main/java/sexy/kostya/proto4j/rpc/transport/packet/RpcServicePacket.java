package sexy.kostya.proto4j.rpc.transport.packet;

import sexy.kostya.proto4j.transport.buffer.Buffer;
import sexy.kostya.proto4j.transport.highlevel.packet.CallbackProto4jPacket;

/**
 * Created by k.shandurenko on 01.10.2020
 */
public class RpcServicePacket extends CallbackProto4jPacket {

    private int serviceID;

    public RpcServicePacket() {

    }

    public RpcServicePacket(int serviceID) {
        this.serviceID = serviceID;
    }

    public int getServiceID() {
        return serviceID;
    }

    @Override
    public int getID() {
        return 3;
    }

    @Override
    public void write(Buffer buffer) {
        buffer.writeInt(this.serviceID);
    }

    @Override
    public void read(Buffer buffer) {
        this.serviceID = buffer.readInt();
    }
}
