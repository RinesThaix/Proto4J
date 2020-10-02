package sexy.kostya.proto4j.rpc.transport;

import org.slf4j.LoggerFactory;
import sexy.kostya.proto4j.rpc.service.ServerServiceManager;
import sexy.kostya.proto4j.rpc.service.ServiceManager;
import sexy.kostya.proto4j.rpc.transport.packet.RpcInvocationPacket;
import sexy.kostya.proto4j.rpc.transport.packet.RpcServicePacket;
import sexy.kostya.proto4j.transport.highlevel.HighChannel;
import sexy.kostya.proto4j.transport.highlevel.base.BaseProto4jHighServer;
import sexy.kostya.proto4j.transport.highlevel.packet.PacketHandler;

/**
 * Created by k.shandurenko on 01.10.2020
 */
public class RpcServer extends BaseProto4jHighServer {

    private final ServerServiceManager serviceManager = new ServerServiceManager();

    public RpcServer(int workerThreads, int handlerThreads) {
        super(LoggerFactory.getLogger("RpcServer"), workerThreads, handlerThreads);
        setPacketManager(new RpcPacketManager());
        setPacketHandler(new PacketHandler<HighChannel>() {

            {
                register(RpcInvocationPacket.class, serviceManager::invokeRemote);
                register(RpcServicePacket.class, (channel, packet) -> serviceManager.register(channel, packet.getServiceID()));
            }

        });
        addOnDisconnect(this.serviceManager::unregister);
    }

    public ServiceManager getServiceManager() {
        return this.serviceManager;
    }

}
