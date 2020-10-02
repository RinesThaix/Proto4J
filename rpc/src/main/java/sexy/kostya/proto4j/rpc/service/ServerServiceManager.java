package sexy.kostya.proto4j.rpc.service;

import com.google.common.base.Preconditions;
import sexy.kostya.proto4j.rpc.transport.packet.RpcInvocationPacket;
import sexy.kostya.proto4j.rpc.transport.packet.RpcResponsePacket;
import sexy.kostya.proto4j.transport.highlevel.HighChannel;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Created by k.shandurenko on 02.10.2020
 */
@SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
public class ServerServiceManager extends BaseServiceManager {

    private final Map<Integer, List<HighChannel>> implementations = new ConcurrentHashMap<>();
    private final Map<HighChannel, Set<Integer>>  revert          = new ConcurrentHashMap<>();

    public void register(HighChannel channel, int serviceID) {
        List<HighChannel> list = getImplementations(serviceID);
        synchronized (list) {
            list.add(channel);
            this.implementations.put(serviceID, list); // because of possible synchronization problems
        }
        Set<Integer> set = this.revert.computeIfAbsent(channel, c -> new HashSet<>());
        synchronized (set) {
            set.add(serviceID);
        }
    }

    public void unregister(HighChannel channel) {
        Set<Integer> set = this.revert.remove(channel);
        if (set == null) {
            return;
        }
        synchronized (set) {
            set.forEach(serviceID -> {
                List<HighChannel> list = this.implementations.get(serviceID);
                if (list == null) {
                    return;
                }
                synchronized (list) {
                    list.remove(channel);
                    if (list.isEmpty()) {
                        this.implementations.remove(serviceID);
                    }
                }
            });
        }
    }

    private List<HighChannel> getImplementations(int serviceID) {
        return this.implementations.computeIfAbsent(serviceID, sid -> new ArrayList<>());
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
            HighChannel channel = getChannel(packet);
            short callbackID = packet.getCallbackID();
            if (channel == null || !channel.isActive()) {
                if (callbackID != 0) {
                    packet.respond(invoker, new RpcResponsePacket("Could not find implementation for service #1", null));
                }
                return;
            }
            if (callbackID == 0) {
                channel.send(packet);
            } else {
                channel.sendWithCallback(packet).thenAccept(response -> {
                    packet.setCallbackID(callbackID);
                    packet.respond(invoker, response);
                });
            }
        }
    }

    @Override
    protected HighChannel getChannel(RpcInvocationPacket packet) {
        List<HighChannel> list = this.implementations.get(packet.getServiceID());
        if (list == null) {
            System.out.println(packet.getServiceID());
            return null;
        }
        synchronized (list) {
            if (list.isEmpty()) {
                return null;
            }
            if (packet.getIndex() == 0) {
                return list.get(ThreadLocalRandom.current().nextInt(list.size()));
            } else {
                return list.get(Math.abs(packet.getIndex()) % list.size());
            }
        }
    }
}
