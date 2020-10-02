package sexy.kostya.proto4j.transport.lowlevel;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sexy.kostya.proto4j.transport.Channel;
import sexy.kostya.proto4j.transport.buffer.Buffer;
import sexy.kostya.proto4j.transport.util.DatagramHelper;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Created by k.shandurenko on 29.09.2020
 */
public abstract class Proto4jServer<C extends Channel> extends Proto4jSocket<C> {

    protected final ServerChannel<C> channel = new ServerChannel<>(this);

    public Proto4jServer(Logger logger, int workerThreads, int handlerThreads) {
        super(logger, workerThreads, handlerThreads);
    }

    public Proto4jServer(int workerThreads, int handlerThreads) {
        this(LoggerFactory.getLogger("Proto4j Server"), workerThreads, handlerThreads);
    }

    public CompletionStage<Void> start(int port) {
        return start("0.0.0.0", port);
    }

    @Override
    void start0(CompletableFuture<Void> future, String address, int port) throws SocketException {
        super.socket = new DatagramSocket(new InetSocketAddress(address, port));
        Thread thread = new Thread(() -> {
            getLogger().info("Listening on {}:{}", address, port);
            future.complete(null);
            try {
                while (true) {
                    byte[]         array  = new byte[DatagramHelper.MAX_DATAGRAM_SIZE];
                    DatagramPacket packet = new DatagramPacket(array, array.length);
                    super.socket.receive(packet);
                    getWorkers().execute(() -> {
                        ByteBuf buffer = Unpooled.wrappedBuffer(packet.getData(), packet.getOffset(), packet.getLength());
//                            DatagramHelper.log("C -> S: ", buffer);
                        this.channel.get(new InetSocketAddress(packet.getAddress(), packet.getPort())).recv(Buffer.wrap(buffer));
                    });
                }
            } catch (IOException e) {
                getLogger().error("Could not receive datagram", e);
            }
        }, "Proto4j Server Thread");
        thread.start();
    }

}
