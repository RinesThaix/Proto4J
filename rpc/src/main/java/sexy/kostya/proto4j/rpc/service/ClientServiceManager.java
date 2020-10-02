package sexy.kostya.proto4j.rpc.service;

import com.google.common.base.Preconditions;
import sexy.kostya.proto4j.exception.RpcException;
import sexy.kostya.proto4j.rpc.transport.RpcClient;
import sexy.kostya.proto4j.rpc.transport.packet.RpcInvocationPacket;
import sexy.kostya.proto4j.rpc.transport.packet.RpcResponsePacket;
import sexy.kostya.proto4j.rpc.transport.packet.RpcServicePacket;
import sexy.kostya.proto4j.transport.highlevel.HighChannel;

import java.lang.ref.WeakReference;

/**
 * Created by k.shandurenko on 02.10.2020
 */
public class ClientServiceManager extends BaseServiceManager {

    private final WeakReference<RpcClient> client;

    public ClientServiceManager(RpcClient client) {
        this.client = new WeakReference<>(client);
    }

    private RpcClient getClient() {
        RpcClient client = this.client.get();
        Preconditions.checkNotNull(client);
        return client;
    }

    @Override
    public void invokeRemote(HighChannel invoker, RpcInvocationPacket packet) {
        if (isServiceRegisteredThere(packet.getServiceID())) {
            invoke(packet).thenAccept(response -> {
                if (response == null) {
                    return;
                }
                Preconditions.checkState(packet.getCallbackID() != 0); // ensure it's awaiting response
                packet.respond(invoker, response);
            });
        } else {
            if (packet.getCallbackID() == 0) {
                return;
            }
            packet.respond(invoker, new RpcResponsePacket(new RpcException(RpcException.Code.NO_SERVICE_AVAILABLE, "Packet came to the application where service implementation is not present"), null));
        }
    }

    @Override
    protected HighChannel getChannel(RpcInvocationPacket packet) {
        return getClient().getChannel();
    }

    @Override
    public <S, I extends S> int registerService(Class<S> serviceInterface, I implementation) {
        int serviceIdentifier = super.registerService(serviceInterface, implementation);
        getClient().getChannel().send(new RpcServicePacket(serviceIdentifier));
        return serviceIdentifier;
    }
}
