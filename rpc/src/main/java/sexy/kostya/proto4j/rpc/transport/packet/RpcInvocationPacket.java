package sexy.kostya.proto4j.rpc.transport.packet;

import sexy.kostya.proto4j.transport.buffer.Buffer;
import sexy.kostya.proto4j.transport.highlevel.packet.CallbackProto4jPacket;

/**
 * Created by k.shandurenko on 01.10.2020
 */
public class RpcInvocationPacket extends CallbackProto4jPacket {

    private int    serviceID;
    private int    methodID;
    private int    index;
    private boolean broadcast;
    private byte[] arguments;

    public RpcInvocationPacket() {
    }

    public RpcInvocationPacket(int serviceID, int methodID, int index, boolean broadcast, byte[] arguments) {
        this.serviceID = serviceID;
        this.methodID = methodID;
        this.index = index;
        this.broadcast = broadcast;
        this.arguments = arguments;
    }

    public int getServiceID() {
        return serviceID;
    }

    public int getMethodID() {
        return methodID;
    }

    public int getIndex() {
        return index;
    }

    public boolean isBroadcast() {
        return broadcast;
    }

    public byte[] getArguments() {
        return arguments;
    }

    @Override
    public int getID() {
        return 1;
    }

    @Override
    public void write(Buffer buffer) {
        buffer.writeInt(this.serviceID);
        buffer.writeInt(this.methodID);
        buffer.writeInt(this.index);
        buffer.writeBoolean(this.broadcast);
        buffer.writeVarInt(this.arguments.length);
        buffer.writeBytes(this.arguments);
    }

    @Override
    public void read(Buffer buffer) {
        this.serviceID = buffer.readInt();
        this.methodID = buffer.readInt();
        this.index = buffer.readInt();
        this.broadcast = buffer.readBoolean();
        this.arguments = new byte[buffer.readVarInt()];
        buffer.readBytes(this.arguments);
    }

}
