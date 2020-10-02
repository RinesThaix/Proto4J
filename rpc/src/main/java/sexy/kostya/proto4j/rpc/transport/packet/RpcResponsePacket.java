package sexy.kostya.proto4j.rpc.transport.packet;

import sexy.kostya.proto4j.transport.buffer.Buffer;
import sexy.kostya.proto4j.transport.highlevel.packet.CallbackProto4jPacket;

/**
 * Created by k.shandurenko on 01.10.2020
 */
public class RpcResponsePacket extends CallbackProto4jPacket {

    private String error;
    private byte[] response;

    public RpcResponsePacket() {
    }

    public RpcResponsePacket(String error, byte[] response) {
        this.error = error;
        this.response = response;
    }

    public String getError() {
        return this.error;
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
        buffer.writeStringMaybe(this.error);
        if (this.error == null) {
            buffer.writeVarInt(this.response.length);
            buffer.writeBytes(this.response);
        }
    }

    @Override
    public void read(Buffer buffer) {
        this.error = buffer.readStringMaybe();
        if (this.error == null) {
            this.response = new byte[buffer.readVarInt()];
            buffer.readBytes(this.response);
        }
    }
}
