package sexy.kostya.proto4j.rpc.transport;

import com.google.common.util.concurrent.ListenableFuture;
import org.slf4j.LoggerFactory;
import sexy.kostya.proto4j.rpc.ServiceProxy;
import sexy.kostya.proto4j.rpc.transport.packet.RpcInvocationPacket;
import sexy.kostya.proto4j.transport.highlevel.HighChannel;
import sexy.kostya.proto4j.transport.highlevel.base.BaseProto4jHighClient;
import sexy.kostya.proto4j.transport.highlevel.packet.CallbackProto4jPacket;
import sexy.kostya.proto4j.transport.highlevel.packet.PacketHandler;

import java.util.concurrent.Executor;

/**
 * Created by k.shandurenko on 01.10.2020
 */
public class RpcClient extends BaseProto4jHighClient {

    private final ServiceProxy proxy;

    public RpcClient(int workerThreads, int handlerThreads) {
        super(LoggerFactory.getLogger("RpcClient"), workerThreads, handlerThreads);
        setPacketManager(new RpcPacketManager());
        setPacketHandler(new PacketHandler<HighChannel>() {

            {
                register(RpcInvocationPacket.class, (channel, packet) -> {

                });
            }

        });

        this.proxy = new ServiceProxy() {
            @Override
            public Executor getExecutor() {
                return getHandlers();
            }

            @Override
            public void send(RpcInvocationPacket packet) {
                getChannel().send(packet);
            }

            @Override
            public ListenableFuture<CallbackProto4jPacket> sendWithCallback(RpcInvocationPacket packet) {
                return getChannel().sendWithCallback(packet);
            }
        };
    }

    public ServiceProxy getProxy() {
        return proxy;
    }
}
