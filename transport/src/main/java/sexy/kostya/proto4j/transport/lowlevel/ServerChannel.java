package sexy.kostya.proto4j.transport.lowlevel;

import sexy.kostya.proto4j.transport.Channel;
import sexy.kostya.proto4j.transport.packet.PacketCodec;
import sexy.kostya.proto4j.transport.packet.Proto4jPacket;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Created by k.shandurenko on 30.09.2020
 */
public class ServerChannel<C extends Channel> {

    private final Map<InetSocketAddress, C> channels = new ConcurrentHashMap<>();
    private final Proto4jServer<C>          server;

    public ServerChannel(Proto4jServer<C> server) {
        this.server = server;
    }

    public C get(InetSocketAddress address) {
        return this.channels.computeIfAbsent(address, ad -> {
            PacketCodec codec   = new PacketCodec(this.server.getSocket(), ad);
            C           channel = this.server.createChannel(codec);

            BiConsumer<C, Proto4jPacket> handler = this.server.getInitialPacketHandler();
            if (handler != null) {
                channel.setHandler(this.server.getHandlers(), packet -> handler.accept(channel, packet));
            }
            return channel;
        });
    }

    public Map<InetSocketAddress, C> getAll() {
        return this.channels;
    }

    public void remove(InetSocketAddress address) {
        this.channels.remove(address);
    }

    public void clear() {
        this.channels.clear();
    }

}
