package sexy.kostya.proto4j.transport.lowlevel;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import org.slf4j.Logger;
import sexy.kostya.proto4j.exception.Proto4jException;
import sexy.kostya.proto4j.transport.Channel;
import sexy.kostya.proto4j.transport.NamedThreadFactory;
import sexy.kostya.proto4j.transport.packet.PacketCodec;
import sexy.kostya.proto4j.transport.packet.Proto4jPacket;

import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;

/**
 * Created by k.shandurenko on 30.09.2020
 */
public abstract class Proto4jSocket<C extends Channel> {

    private final Logger logger;
    DatagramSocket socket;
    private final Executor workers;
    private final Executor handlers;

    private final Thread shutdownHook;

    private BiConsumer<C, Proto4jPacket> initialPacketHandler;

    Proto4jSocket(Logger logger, int workerThreads, int handlerThreads) {
        this.logger = logger;
        this.workers = Executors.newFixedThreadPool(workerThreads, new NamedThreadFactory("Proto4j Worker Thread", true));
        this.handlers = Executors.newFixedThreadPool(handlerThreads, new NamedThreadFactory("Proto4j Handler Thread", true));

        this.shutdownHook = new Thread(this::stop0, "Proto4j Socket Shutdown Hook");
    }

    public ListenableFuture<Void> start(String address, int port) {
        SettableFuture<Void> future = SettableFuture.create();
        if (this.socket != null) {
            future.setException(new Proto4jException("Socket is already started"));
            return future;
        }
        Runtime.getRuntime().addShutdownHook(this.shutdownHook);
        try {
            start0(future, address, port);
        } catch (SocketException ex) {
            future.setException(ex);
        }
        return future;
    }

    abstract void start0(SettableFuture<Void> future, String address, int port) throws SocketException;

    public void stop() {
        if (stop0()) {
            Runtime.getRuntime().removeShutdownHook(this.shutdownHook);
        }
    }

    protected boolean stop0() {
        if (this.socket != null) {
            this.socket.close();
            this.socket = null;
            return true;
        }
        return false;
    }

    public Logger getLogger() {
        return logger;
    }

    public DatagramSocket getSocket() {
        return socket;
    }

    public Executor getWorkers() {
        return workers;
    }

    public Executor getHandlers() {
        return handlers;
    }

    public BiConsumer<C, Proto4jPacket> getInitialPacketHandler() {
        return initialPacketHandler;
    }

    public void setInitialPacketHandler(BiConsumer<C, Proto4jPacket> initialPacketHandler) {
        this.initialPacketHandler = initialPacketHandler;
    }

    public abstract C createChannel(PacketCodec codec);

}