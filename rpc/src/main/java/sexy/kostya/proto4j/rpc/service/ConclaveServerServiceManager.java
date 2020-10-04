package sexy.kostya.proto4j.rpc.service;

import com.google.common.base.Preconditions;
import sexy.kostya.proto4j.exception.RpcException;
import sexy.kostya.proto4j.rpc.transport.conclave.ConclaveChannel;
import sexy.kostya.proto4j.rpc.transport.conclave.RpcConclaveServer;
import sexy.kostya.proto4j.rpc.transport.conclave.packet.RpcDisconnectNotificationPacket;
import sexy.kostya.proto4j.rpc.transport.conclave.packet.RpcServiceNotificationPacket;
import sexy.kostya.proto4j.rpc.transport.packet.RpcInvocationPacket;
import sexy.kostya.proto4j.rpc.transport.packet.RpcResponsePacket;
import sexy.kostya.proto4j.transport.highlevel.packet.EnumeratedProto4jPacket;
import sexy.kostya.proto4j.transport.util.DatagramHelper;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.*;

/**
 * Created by k.shandurenko on 03.10.2020
 */
public class ConclaveServerServiceManager extends BaseServiceManager<ConclaveChannel> {

    private final ConclaveChannel                  self;
    private final WeakReference<RpcConclaveServer> server;

    private final Map<ConclaveChannel, ServerData> servers = new ConcurrentHashMap<>();

    private final Map<Integer, TreeSet<ServiceChannel>> services = new ConcurrentHashMap<>();

    public ConclaveServerServiceManager(RpcConclaveServer server) {
        this.self = server.getSelfChannel();
        this.server = new WeakReference<>(server);
        addServer(this.self);
    }

    public void addServer(ConclaveChannel channel) {
        this.servers.put(channel, new ServerData());
    }

    public void removeServer(ConclaveChannel channel) {
        this.servers.remove(channel);
        this.services.values().forEach(set -> {
            synchronized (set) {
                set.removeIf(ch -> ch.serverChannel == channel);
            }
        });
    }

    public void serviceRegistered(ConclaveChannel channel, int channelID, int serviceID) {
        ServerData data = this.servers.get(channel);
        Preconditions.checkNotNull(data);
        List<Integer> list = data.implementations.computeIfAbsent(serviceID, sid -> new ArrayList<>());
        synchronized (list) {
            list.add(channelID);
            data.implementations.put(serviceID, list);
        }
        Set<Integer> set = data.revert.computeIfAbsent(channelID, cid -> new HashSet<>());
        synchronized (set) {
            set.add(serviceID);
        }
        TreeSet<ServiceChannel> channels = this.services.computeIfAbsent(serviceID, sid -> new TreeSet<>());
        synchronized (channels) {
            channels.add(new ServiceChannel(channel, channelID));
            this.services.put(serviceID, channels);
        }
    }

    public void channelUnregistered(ConclaveChannel channel, int channelID) {
        ServerData data = this.servers.get(channel);
        if (data == null) {
            return;
        }
        Set<Integer> set = data.revert.remove(channelID);
        if (set == null) {
            return;
        }
        synchronized (set) {
            set.forEach(serviceID -> {
                List<Integer> list = data.implementations.get(serviceID);
                Preconditions.checkNotNull(list);
                synchronized (list) {
                    list.remove((Integer) channelID);
                    if (list.isEmpty()) {
                        data.implementations.remove(serviceID);
                    }
                }
                TreeSet<ServiceChannel> channels = this.services.get(serviceID);
                Preconditions.checkNotNull(channels);
                synchronized (channels) {
                    channels.remove(new ServiceChannel(channel, channelID));
                    if (channels.isEmpty()) {
                        this.services.remove(serviceID);
                    }
                }
            });
        }
    }

    public void register(ConclaveChannel channel, int serviceID) {
        int channelID = channel.getId();
        serviceRegistered(this.self, channelID, serviceID);
        broadcast(null, new RpcServiceNotificationPacket(channel.getId(), serviceID));
    }

    public void unregister(ConclaveChannel channel) {
        int channelID = channel.getId();
        channelUnregistered(this.self, channelID);
        broadcast(channel, new RpcDisconnectNotificationPacket(channel.getId()));
    }

    @Override
    public void send(RpcInvocationPacket packet) {
        ConclaveChannel channel = getChannel(packet);
        if (channel == this.self) {
            invoke(packet);
        } else if (channel == null || !channel.isActive()) {
            throw new NullPointerException("Could not find implementation for service");
        } else {
            channel.send(packet);
        }
    }

    @Override
    public CompletionStage<RpcResponsePacket> sendWithCallback(RpcInvocationPacket packet) {
        ConclaveChannel channel = getChannel(packet);
        if (channel == this.self) {
            return invoke(packet);
        } else if (channel == null || !channel.isActive()) {
            return CompletableFuture.completedFuture(new RpcResponsePacket(new RpcException(RpcException.Code.NO_SERVICE_AVAILABLE, "Could not find implementation for service"), null));
        } else {
            return channel.sendWithCallback(packet).thenApply(p -> (RpcResponsePacket) p);
        }
    }

    @Override
    public void invokeRemote(ConclaveChannel invoker, RpcInvocationPacket packet) {
        if (!packet.isBroadcast()) {
            ConclaveChannel channel    = getChannel(packet);
            short           callbackID = packet.getCallbackID();
            if (channel == null || !channel.isActive()) {
                if (callbackID != 0) {
                    packet.respond(invoker, new RpcResponsePacket(new RpcException(RpcException.Code.NO_SERVICE_AVAILABLE, "Could not find implementation for service"), null));
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
        } else if (invoker.isServer()) {
            List<Integer> list = this.servers.get(this.self).implementations.get(packet.getServiceID());
            if (list == null) {
                return;
            }
            short callbackID = packet.getCallbackID();
            if (callbackID == 0) {
                synchronized (list) {
                    RpcConclaveServer server = getServer();
                    list.forEach(channelID -> {
                        ConclaveChannel channel = server.getChannel(channelID);
                        if (channel == null || !channel.isActive()) {
                            return;
                        }
                        channel.send(packet);
                    });
                }
            } else {
                CountDownLatch latch;
                synchronized (list) {
                    RpcConclaveServer server = getServer();
                    latch = new CountDownLatch(list.size());
                    list.forEach(channelID -> {
                        ConclaveChannel channel = server.getChannel(channelID);
                        if (channel == null || !channel.isActive()) {
                            latch.countDown();
                            if (latch.getCount() == 0) {
                                packet.setCallbackID(callbackID);
                                packet.respond(invoker, new RpcResponsePacket(null, DatagramHelper.ZERO_LENGTH_ARRAY));
                            }
                            return;
                        }
                        channel.sendWithCallback(packet).thenAccept(p -> {
                            latch.countDown();
                            if (latch.getCount() == 0) {
                                packet.setCallbackID(callbackID);
                                packet.respond(invoker, p);
                            }
                        });
                    });
                }
            }
        } else {
            short callbackID = packet.getCallbackID();
            if (callbackID == 0) {
                RpcConclaveServer server = getServer();
                this.servers.forEach((channel, data) -> {
                    List<Integer> list = data.implementations.get(packet.getServiceID());
                    if (list == null) {
                        return;
                    }
                    synchronized (list) {
                        if (list.isEmpty()) {
                            return;
                        }
                        if (channel == this.self) {
                            list.forEach(channelID -> server.getChannel(channelID).send(packet));
                            return;
                        }
                    }
                    channel.send(packet);
                });
            } else {
                Map<ConclaveChannel, Integer> channels = new HashMap<>();
                this.servers.forEach((channel, data) -> {
                    List<Integer> list = data.implementations.get(packet.getServiceID());
                    if (list == null) {
                        return;
                    }
                    synchronized (list) {
                        if (list.isEmpty()) {
                            return;
                        }
                        channels.put(channel, channel == this.self ? list.size() : 1);
                    }
                });
                CountDownLatch latch = new CountDownLatch(channels.values().stream().mapToInt(i -> i).sum());
                channels.keySet().forEach(channel -> {
                    if (channel == this.self) {
                        List<Integer> list = this.servers.get(channel).implementations.get(packet.getServiceID());
                        if (list == null) {
                            return;
                        }
                        RpcConclaveServer server = getServer();
                        synchronized (list) {
                            list.forEach(channelID -> server.getChannel(channelID).sendWithCallback(packet).thenAccept(p -> {
                                latch.countDown();
                                if (latch.getCount() == 0) {
                                    packet.setCallbackID(callbackID);
                                    packet.respond(invoker, p);
                                }
                            }));
                        }
                    } else {
                        channel.sendWithCallback(packet).thenAccept(p -> {
                            latch.countDown();
                            if (latch.getCount() == 0) {
                                packet.setCallbackID(callbackID);
                                packet.respond(invoker, p);
                            }
                        });
                    }
                });
            }
        }
    }

    @Override
    protected ConclaveChannel getChannel(RpcInvocationPacket packet) {
        int                     index    = Math.abs(packet.getIndex() == 0 ? ThreadLocalRandom.current().nextInt() : packet.getIndex());
        TreeSet<ServiceChannel> channels = this.services.get(packet.getServiceID());
        if (channels == null) {
            return null;
        }
        synchronized (channels) {
            index %= channels.size();
            Iterator<ServiceChannel> iterator = channels.iterator();
            for (int i = 0; i < index; ++i) {
                iterator.next();
            }
            ServiceChannel  channel = iterator.next();
            ConclaveChannel ch      = channel.serverChannel;
            if (ch == this.self) {
                return getServer().getChannel(channel.channelID);
            } else {
                return ch;
            }
        }
    }

    private void broadcast(ConclaveChannel ignored, EnumeratedProto4jPacket packet) {
        this.servers.keySet().stream().filter(channel -> channel.getId() != 0 && channel != ignored).forEach(channel -> channel.send(packet));
    }

    private RpcConclaveServer getServer() {
        RpcConclaveServer server = this.server.get();
        Preconditions.checkNotNull(server);
        return server;
    }

    private static class ServerData {

        // serviceID -> List<channelID>
        private final Map<Integer, List<Integer>> implementations = new ConcurrentHashMap<>();

        // channelID -> Set<serviceID>
        private final Map<Integer, Set<Integer>> revert = new ConcurrentHashMap<>();

    }

    private static class ServiceChannel implements Comparable<ServiceChannel> {

        private final ConclaveChannel serverChannel;
        private final int             channelID;

        public ServiceChannel(ConclaveChannel serverChannel, int channelID) {
            this.serverChannel = serverChannel;
            this.channelID = channelID;
        }

        @Override
        public int compareTo(ServiceChannel sc) {
            return Integer.compare(this.channelID, sc.channelID);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ServiceChannel that = (ServiceChannel) o;
            return channelID == that.channelID &&
                    Objects.equals(serverChannel, that.serverChannel);
        }

        @Override
        public int hashCode() {
            return Objects.hash(serverChannel, channelID);
        }
    }

}
