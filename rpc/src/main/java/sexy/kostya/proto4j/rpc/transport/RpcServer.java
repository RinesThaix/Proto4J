package sexy.kostya.proto4j.rpc.transport;

import org.slf4j.LoggerFactory;
import sexy.kostya.proto4j.rpc.transport.packet.RpcInvocationPacket;
import sexy.kostya.proto4j.rpc.transport.packet.RpcResponsePacket;
import sexy.kostya.proto4j.rpc.transport.packet.RpcServicePacket;
import sexy.kostya.proto4j.transport.highlevel.HighChannel;
import sexy.kostya.proto4j.transport.highlevel.base.BaseProto4jHighServer;
import sexy.kostya.proto4j.transport.highlevel.packet.PacketHandler;

/**
 * Created by k.shandurenko on 01.10.2020
 */
public class RpcServer extends BaseProto4jHighServer {

    public RpcServer(int workerThreads, int handlerThreads) {
        super(LoggerFactory.getLogger("RpcServer"), workerThreads, handlerThreads);
        setPacketManager(new RpcPacketManager());
        setPacketHandler(new PacketHandler<HighChannel>() {

            {
                register(RpcInvocationPacket.class, (channel, packet) -> {

                });
                register(RpcServicePacket.class, (channel, packet) -> {
                    if (packet.isRegister()) {

                    } else {

                    }
                });
            }

        });
    }

}
