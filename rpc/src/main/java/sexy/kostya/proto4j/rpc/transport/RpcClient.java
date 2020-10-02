package sexy.kostya.proto4j.rpc.transport;

import org.slf4j.LoggerFactory;
import sexy.kostya.proto4j.rpc.service.ClientServiceManager;
import sexy.kostya.proto4j.rpc.service.ServiceManager;
import sexy.kostya.proto4j.rpc.transport.packet.RpcInvocationPacket;
import sexy.kostya.proto4j.transport.highlevel.HighChannel;
import sexy.kostya.proto4j.transport.highlevel.base.BaseProto4jHighClient;
import sexy.kostya.proto4j.transport.highlevel.packet.PacketHandler;

/**
 * Created by k.shandurenko on 01.10.2020
 */
public class RpcClient extends BaseProto4jHighClient {

    private final ClientServiceManager serviceManager = new ClientServiceManager(this);

    public RpcClient(int workerThreads, int handlerThreads) {
        super(LoggerFactory.getLogger("RpcClient"), workerThreads, handlerThreads);
        setPacketManager(new RpcPacketManager());
        setPacketHandler(new PacketHandler<HighChannel>() {

            {
                register(RpcInvocationPacket.class, serviceManager::invokeRemote);
            }

        });
    }

    public ServiceManager getServiceManager() {
        return this.serviceManager;
    }
}
