package sexy.kostya.proto4j.transport.highlevel.packet.def;

import sexy.kostya.proto4j.transport.highlevel.packet.PacketManager;

/**
 * Created by k.shandurenko on 01.10.2020
 */
public class DefaultPacketManager extends PacketManager {

    @SuppressWarnings("unchecked")
    public DefaultPacketManager() {
        register(
                Packet1Ping::new,
                Packet2Disconnect::new
        );
    }

}
