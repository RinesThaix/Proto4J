package sexy.kostya.proto4j.rpc.transport.conclave;

import sexy.kostya.proto4j.rpc.transport.RpcPacketManager;
import sexy.kostya.proto4j.rpc.transport.conclave.packet.RpcDisconnectNotificationPacket;
import sexy.kostya.proto4j.rpc.transport.conclave.packet.RpcServerPacket;
import sexy.kostya.proto4j.rpc.transport.conclave.packet.RpcServiceNotificationPacket;

/**
 * Created by k.shandurenko on 01.10.2020
 */
public class RpcConclavePacketManager extends RpcPacketManager {

    public RpcConclavePacketManager() {
        super();
        register(
                RpcServerPacket::new,
                RpcServiceNotificationPacket::new,
                RpcDisconnectNotificationPacket::new
        );
    }
}
