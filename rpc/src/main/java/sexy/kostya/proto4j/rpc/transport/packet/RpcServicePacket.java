package sexy.kostya.proto4j.rpc.transport.packet;

import sexy.kostya.proto4j.transport.buffer.Buffer;
import sexy.kostya.proto4j.transport.highlevel.packet.EnumeratedProto4jPacket;

/**
 * Created by k.shandurenko on 01.10.2020
 */
public class RpcServicePacket extends EnumeratedProto4jPacket {

    private int     serviceID;
    private boolean register;

    public RpcServicePacket() {

    }

    public RpcServicePacket(int serviceID, boolean register) {
        this.serviceID = serviceID;
        this.register = register;
    }

    public int getServiceID() {
        return serviceID;
    }

    public boolean isRegister() {
        return register;
    }

    @Override
    public int getID() {
        return 3;
    }

    @Override
    public void write(Buffer buffer) {
        buffer.writeInt(this.serviceID);
        buffer.writeBoolean(this.register);
    }

    @Override
    public void read(Buffer buffer) {
        this.serviceID = buffer.readInt();
        this.register = buffer.readBoolean();
    }
}
