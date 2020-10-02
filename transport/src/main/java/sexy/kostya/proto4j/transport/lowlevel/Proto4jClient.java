package sexy.kostya.proto4j.transport.lowlevel;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sexy.kostya.proto4j.transport.Channel;
import sexy.kostya.proto4j.transport.buffer.Buffer;
import sexy.kostya.proto4j.transport.packet.PacketCodec;
import sexy.kostya.proto4j.transport.packet.Proto4jPacket;
import sexy.kostya.proto4j.transport.util.DatagramHelper;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;

/**
 * Created by k.shandurenko on 29.09.2020
 */
public abstract class Proto4jClient<C extends Channel> extends Proto4jSocket<C> {

    private C channel;

    public Proto4jClient(Logger logger, int workerThreads, int handlerThreads) {
        super(logger, workerThreads, handlerThreads);
    }

    public Proto4jClient(int workerThreads, int handlerThreads) {
        this(LoggerFactory.getLogger("Proto4j Client"), workerThreads, handlerThreads);
    }

    public CompletionStage<Void> connect(String address, int port) {
        return start(address, port);
    }

    @Override
    void start0(CompletableFuture<Void> future, String address, int port) throws SocketException {
        InetSocketAddress remoteAddress = new InetSocketAddress(address, port);
        super.socket = new DatagramSocket();
        this.channel = createChannel(new PacketCodec(this.socket, remoteAddress));
        BiConsumer<C, Proto4jPacket> handler = getInitialPacketHandler();
        if (handler != null) {
            this.channel.setHandler(getHandlers(), packet -> handler.accept(this.channel, packet));
        }
        Thread thread = new Thread(() -> {
            getLogger().info("Started the client");
            future.complete(null);
            try {
                while (true) {
                    byte[]         array  = new byte[DatagramHelper.MAX_DATAGRAM_SIZE];
                    DatagramPacket packet = new DatagramPacket(array, array.length);
                    super.socket.receive(packet);
                    getWorkers().execute(() -> {
                        ByteBuf buffer = Unpooled.wrappedBuffer(packet.getData(), packet.getOffset(), packet.getLength());
//                            DatagramHelper.log("S -> C: ", buffer);
                        InetSocketAddress addr = new InetSocketAddress(packet.getAddress(), packet.getPort());
                        if (!remoteAddress.equals(addr)) {
                            getLogger().warn("Received packet from an unknown address: {}", addr);
                            return;
                        }
                        this.channel.recv(Buffer.wrap(buffer));
                    });
                }
            } catch (IOException e) {
                getLogger().error("Could not receive datagram", e);
            }
        }, "Proto4j Client Thread");
        thread.start();
    }

    public C getChannel() {
        return this.channel;
    }

}
