package sexy.kostya.proto4j.rpc.transport.conclave.packet;

import sexy.kostya.proto4j.transport.buffer.Buffer;
import sexy.kostya.proto4j.transport.highlevel.packet.CallbackProto4jPacket;

/**
 * Created by k.shandurenko on 03.10.2020
 */
public class RpcServerPacket extends CallbackProto4jPacket {
    @Override
    public int getID() {
        return 4;
    }

    @Override
    public void write(Buffer buffer) {

    }

    @Override
    public void read(Buffer buffer) {

    }
}
