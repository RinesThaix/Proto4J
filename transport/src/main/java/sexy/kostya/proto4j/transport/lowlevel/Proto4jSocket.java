package sexy.kostya.proto4j.transport.lowlevel;

import org.slf4j.Logger;
import sexy.kostya.proto4j.commons.Proto4jException;
import sexy.kostya.proto4j.transport.Channel;
import sexy.kostya.proto4j.transport.NamedThreadFactory;
import sexy.kostya.proto4j.transport.packet.PacketCodec;
import sexy.kostya.proto4j.transport.packet.Proto4jPacket;

import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
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

    protected final Thread shutdownHook;

    private BiConsumer<C, Proto4jPacket> initialPacketHandler;

    Proto4jSocket(Logger logger, int workerThreads, int handlerThreads) {
        this.logger = logger;
        this.workers = Executors.newFixedThreadPool(workerThreads, new NamedThreadFactory("Proto4j Worker Thread", true));
        this.handlers = Executors.newFixedThreadPool(handlerThreads, new NamedThreadFactory("Proto4j Handler Thread", true));

        this.shutdownHook = new Thread(this::shutdownInternally, "Proto4j Socket Shutdown Hook");
    }

    public CompletionStage<Void> start(String address, int port) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        if (this.socket != null) {
            future.completeExceptionally(new Proto4jException("Socket is already started"));
            return future;
        }
        Runtime.getRuntime().addShutdownHook(this.shutdownHook);
        try {
            start0(future, address, port);
        } catch (SocketException ex) {
            future.completeExceptionally(ex);
        }
        return future;
    }

    abstract void start0(CompletableFuture<Void> future, String address, int port) throws SocketException;

    public final void shutdown() {
        if (shutdownInternally()) {
            Runtime.getRuntime().removeShutdownHook(this.shutdownHook);
        }
    }

    protected boolean shutdownInternally() {
        DatagramSocket socket = this.socket;
        this.socket = null;
        if (socket != null) {
            getLogger().info("Shutting down");
            socket.close();
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
