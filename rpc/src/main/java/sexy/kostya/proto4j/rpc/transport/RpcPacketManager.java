package sexy.kostya.proto4j.rpc.transport;

import sexy.kostya.proto4j.rpc.transport.packet.RpcInvocationPacket;
import sexy.kostya.proto4j.rpc.transport.packet.RpcResponsePacket;
import sexy.kostya.proto4j.rpc.transport.packet.RpcServicePacket;
import sexy.kostya.proto4j.transport.highlevel.packet.def.DefaultPacketManager;

/**
 * Created by k.shandurenko on 01.10.2020
 */
public class RpcPacketManager extends DefaultPacketManager {

    public RpcPacketManager() {
        register(
                RpcInvocationPacket::new,
                RpcResponsePacket::new,
                RpcServicePacket::new
        );
    }
}
