package sexy.kostya.proto4j.rpc.transport.conclave;

import org.slf4j.LoggerFactory;
import sexy.kostya.proto4j.rpc.service.ConclaveServerServiceManager;
import sexy.kostya.proto4j.rpc.transport.conclave.packet.RpcDisconnectNotificationPacket;
import sexy.kostya.proto4j.rpc.transport.conclave.packet.RpcServerPacket;
import sexy.kostya.proto4j.rpc.transport.conclave.packet.RpcServiceNotificationPacket;
import sexy.kostya.proto4j.rpc.transport.packet.RpcInvocationPacket;
import sexy.kostya.proto4j.rpc.transport.packet.RpcServicePacket;
import sexy.kostya.proto4j.transport.highlevel.Proto4jHighClient;
import sexy.kostya.proto4j.transport.highlevel.Proto4jHighServer;
import sexy.kostya.proto4j.transport.highlevel.packet.PacketHandler;
import sexy.kostya.proto4j.transport.packet.PacketCodec;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Created by k.shandurenko on 03.10.2020
 */
public class RpcConclaveServer extends Proto4jHighServer<ConclaveChannel> {

    private final ConclaveChannel               self;
    private final Map<Integer, ConclaveChannel> channels = new ConcurrentHashMap<>();

    private final List<InetSocketAddress>      allServersAddresses;
    private final ConclaveServerServiceManager serviceManager;

    public RpcConclaveServer(List<InetSocketAddress> allServersAddresses, int workerThreads, int handlerThreads) {
        super(LoggerFactory.getLogger("RpcConclaveServer"), workerThreads, handlerThreads);
        this.allServersAddresses = allServersAddresses;
        this.self = new ConclaveChannel(0, null, null);
        this.channels.put(0, this.self);
        this.serviceManager = new ConclaveServerServiceManager(this);

        setPacketManager(new RpcConclavePacketManager());
        setPacketHandler(new PacketHandler<ConclaveChannel>() {

            {
                register(RpcInvocationPacket.class, serviceManager::invokeRemote);
                register(RpcServicePacket.class, (channel, packet) -> {
                    serviceManager.register(channel, packet.getServiceID());
                    packet.respond(channel, packet);
                });
                register(RpcServerPacket.class, (channel, packet) -> {
                    channel.setServer(true);
                    serviceManager.addServer(channel);
                });
                register(RpcServiceNotificationPacket.class, (channel, packet) -> serviceManager.serviceRegistered(channel, packet.getChannelID(), packet.getServiceID()));
                register(RpcDisconnectNotificationPacket.class, (channel, packet) -> serviceManager.channelUnregistered(channel, packet.getChannelID()));
            }

        });
        addOnDisconnect(channel -> {
            this.serviceManager.unregister(channel);
            if (channel.isServer()) {
                this.serviceManager.removeServer(channel);
            }
            this.channels.remove(channel.getId());
        });
    }

    @Override
    public CompletionStage<Void> start(String address, int port) {
        return super.start(address, port).thenAccept(v -> {
            InetSocketAddress myAddress = new InetSocketAddress(address, port);
            this.allServersAddresses.stream().filter(addr -> !addr.equals(myAddress)).forEach(addr -> {
                RpcConclaveServerClient client = new RpcConclaveServerClient(2, 2);
                try {
                    client.start(addr.getHostName(), addr.getPort()).toCompletableFuture().get(1, TimeUnit.SECONDS);
                } catch (Exception ignored) {
                    client.stop();
                }
            });
        });
    }

    @Override
    public ConclaveChannel createChannel(PacketCodec codec) {
        int    id;
        Random random = ThreadLocalRandom.current();
        do {
            id = random.nextInt();
        } while (this.channels.containsKey(id));
        ConclaveChannel channel = new ConclaveChannel(id, getCallbacksRegistry(), codec);
        this.channels.put(id, channel);
        return channel;
    }

    public ConclaveChannel getSelfChannel() {
        return this.self;
    }

    public ConclaveChannel getChannel(int id) {
        return this.channels.get(id);
    }

    private class RpcConclaveServerClient extends Proto4jHighClient<ConclaveChannel> {

        public RpcConclaveServerClient(int workerThreads, int handlerThreads) {
            super(LoggerFactory.getLogger("RpcConclaveServerClient"), workerThreads, handlerThreads);
            setPacketManager(RpcConclaveServer.this.getPacketManager());
            setPacketHandler(RpcConclaveServer.this.getPacketHandler());
        }

        @Override
        public CompletionStage<Void> start(String address, int port) {
            return super.start(address, port).thenAccept(v -> {
                getChannel().setServer(true);
                serviceManager.addServer(getChannel());
                getChannel().send(new RpcServerPacket());
            });
        }

        @Override
        public ConclaveChannel createChannel(PacketCodec codec) {
            return RpcConclaveServer.this.createChannel(codec);
        }

        @Override
        protected boolean stop0() {
            ConclaveChannel channel = getChannel();
            if (channel != null) {
                serviceManager.unregister(channel);
                serviceManager.removeServer(channel);
                channels.remove(channel.getId());
            }
            return super.stop0();
        }

    }

}
