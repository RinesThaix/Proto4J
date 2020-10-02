package sexy.kostya.proto4j.rpc.transport.packet;

import sexy.kostya.proto4j.exception.RpcException;
import sexy.kostya.proto4j.transport.buffer.Buffer;
import sexy.kostya.proto4j.transport.highlevel.packet.CallbackProto4jPacket;

/**
 * Created by k.shandurenko on 01.10.2020
 */
public class RpcResponsePacket extends CallbackProto4jPacket {

    private RpcException exception;
    private byte[] response;

    public RpcResponsePacket() {
    }

    public RpcResponsePacket(RpcException exception, byte[] response) {
        this.exception = exception;
        this.response = response;
    }

    public RpcException getException() {
        return this.exception;
    }

    public byte[] getResponse() {
        return response;
    }

    @Override
    public int getID() {
        return 2;
    }

    @Override
    public void write(Buffer buffer) {
        if (this.exception == null) {
            buffer.writeBoolean(false);
            buffer.writeVarInt(this.response.length);
            buffer.writeBytes(this.response);
        } else {
            buffer.writeBoolean(true);
            this.exception.write(buffer);
        }
    }

    @Override
    public void read(Buffer buffer) {
        if (buffer.readBoolean()) {
            this.exception = new RpcException();
            this.exception.read(buffer);
        } else {
            this.response = new byte[buffer.readVarInt()];
            buffer.readBytes(this.response);
        }
    }
}
