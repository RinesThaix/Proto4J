package sexy.kostya.proto4j.rpc.service;

import sexy.kostya.proto4j.exception.RpcException;
import sexy.kostya.proto4j.rpc.transport.packet.RpcInvocationPacket;
import sexy.kostya.proto4j.rpc.transport.packet.RpcResponsePacket;
import sexy.kostya.proto4j.transport.highlevel.HighChannel;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Created by k.shandurenko on 02.10.2020
 */
public abstract class BaseServiceManager<C extends HighChannel> extends InternalServiceManagerImpl {

    @Override
    public void send(RpcInvocationPacket packet) {
        if (isServiceRegisteredThere(packet.getServiceID()) && packet.canBeExecutedLocally()) {
            invoke(packet);
        } else {
            C channel = getChannel(packet);
            if (channel == null || !channel.isActive()) {
                throw new NullPointerException("Could not find implementation for service");
            }
            channel.send(packet);
        }
    }

    @Override
    public CompletionStage<RpcResponsePacket> sendWithCallback(RpcInvocationPacket packet) {
        if (isServiceRegisteredThere(packet.getServiceID()) && packet.canBeExecutedLocally()) {
            return invoke(packet);
        } else {
            C channel = getChannel(packet);
            if (channel == null || !channel.isActive()) {
                return CompletableFuture.completedFuture(new RpcResponsePacket(new RpcException(RpcException.Code.NO_SERVICE_AVAILABLE, "Could not find implementation for service"), null));
            }
            return channel.sendWithCallback(packet).thenApply(p -> (RpcResponsePacket) p);
        }
    }

    public abstract void invokeRemote(C invoker, RpcInvocationPacket packet);

    protected abstract C getChannel(RpcInvocationPacket packet);

}
