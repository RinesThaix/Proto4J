package sexy.kostya.proto4j.transport.highlevel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sexy.kostya.proto4j.transport.highlevel.packet.CallbackProto4jPacket;
import sexy.kostya.proto4j.transport.highlevel.packet.EnumeratedProto4jPacket;
import sexy.kostya.proto4j.transport.highlevel.packet.PacketHandler;
import sexy.kostya.proto4j.transport.highlevel.packet.PacketManager;
import sexy.kostya.proto4j.transport.highlevel.packet.def.DefaultPacketManager;
import sexy.kostya.proto4j.transport.highlevel.packet.def.Packet1Ping;
import sexy.kostya.proto4j.transport.highlevel.packet.def.Packet2Disconnect;
import sexy.kostya.proto4j.transport.lowlevel.Proto4jClient;
import sexy.kostya.proto4j.transport.packet.Proto4jPacket;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/**
 * Created by k.shandurenko on 01.10.2020
 */
public abstract class Proto4jHighClient<C extends HighChannel> extends Proto4jClient<C> {

    private       PacketManager     packetManager = new DefaultPacketManager();
    private       PacketHandler<C>  packetHandler = new PacketHandler<>();
    private final CallbacksRegistry callbacksRegistry;

    private CompletableFuture<Void> handshakingFuture;

    public Proto4jHighClient(Logger logger, int workerThreads, int handlerThreads) {
        this(logger, workerThreads, handlerThreads, new CallbacksRegistry());
    }

    public Proto4jHighClient(Logger logger, int workerThreads, int handlerThreads, CallbacksRegistry callbacksRegistry) {
        super(logger, workerThreads, handlerThreads);
        this.callbacksRegistry = callbacksRegistry;
        super.setInitialPacketHandler((channel, packet) -> {
            CompletableFuture<Void> completed = new CompletableFuture<>();
            if (Handshake.processOnClientside(channel, packet.getBuffer(), completed)) {
                channel.handshaked = true;
                channel.setHandler(getHandlers(), p -> {
                    EnumeratedProto4jPacket enumeratedPacket = this.packetManager.readPacket(p.getBuffer());
                    getLogger().debug("Received {} from {}", enumeratedPacket.getClass().getSimpleName(), channel.getCodec().getAddress());
                    switch (enumeratedPacket.getID()) {
                        case Packet1Ping.ID:
                            channel.send(new Packet1Ping());
                            break;
                        case Packet2Disconnect.ID: {
                            Packet2Disconnect casted = (Packet2Disconnect) enumeratedPacket;
                            if (handleCallbackPacket(casted)) {
                                break;
                            }
                            if (casted.getReason() == null) {
                                getLogger().info("Disconnected by server");
                            } else {
                                getLogger().info("Disconnected by server: {}", casted.getReason());
                            }
                            casted.respond(channel, casted, Proto4jPacket.Flag.UNRELIABLE);
                            if (shutdownInternally(false)) {
                                Runtime.getRuntime().removeShutdownHook(super.shutdownHook);
                            }
                            break;
                        }
                        default:
                            if (enumeratedPacket instanceof CallbackProto4jPacket) {
                                if (handleCallbackPacket((CallbackProto4jPacket) enumeratedPacket)) {
                                    break;
                                }
                            }
                            this.packetHandler.handle(channel, enumeratedPacket);
                            break;
                    }
                });
                this.handshakingFuture.complete(null);
            }
            completed.complete(null);
        });
    }

    private boolean handleCallbackPacket(CallbackProto4jPacket packet) {
        if (packet.getCallbackID() < 0) {
            packet.setCallbackID((short) -packet.getCallbackID());
            this.callbacksRegistry.responded(packet);
            return true;
        }
        return false;
    }

    @Override
    public CompletionStage<Void> start(String address, int port) {
        this.handshakingFuture = new CompletableFuture<>();
        super.start(address, port).whenComplete((res, ex) -> {
            if (ex == null) {
                Handshake.initOnClientside(getChannel());
            } else if (this.handshakingFuture != null) {
                this.handshakingFuture.completeExceptionally(ex);
            }
        });
        return this.handshakingFuture;
    }

    public Proto4jHighClient(int workerThreads, int handlerThreads) {
        this(LoggerFactory.getLogger("Proto4j HighClient"), workerThreads, handlerThreads);
    }

    public PacketManager getPacketManager() {
        return packetManager;
    }

    public void setPacketManager(PacketManager packetManager) {
        this.packetManager = packetManager;
    }

    public PacketHandler<C> getPacketHandler() {
        return packetHandler;
    }

    public void setPacketHandler(PacketHandler<C> packetHandler) {
        this.packetHandler = packetHandler;
    }

    public CallbacksRegistry getCallbacksRegistry() {
        return callbacksRegistry;
    }

    @Override
    protected boolean shutdownInternally() {
        return shutdownInternally(true);
    }

    private boolean shutdownInternally(boolean withNotification) {
        C channel = getChannel();
        if (channel == null || !channel.isActive()) {
            return false;
        }
        if (withNotification) {
            try {
                getChannel().sendWithCallback(new Packet2Disconnect()).toCompletableFuture().get(HighChannel.INITIAL_DELAY, TimeUnit.MILLISECONDS);
            } catch (Exception ignored) {
            }
        }
        if (!super.shutdownInternally()) {
            return false;
        }
        channel.active = false;
        if (this.handshakingFuture != null) {
            if (!this.handshakingFuture.isDone()) {
                this.handshakingFuture.completeExceptionally(new Exception("Disconnected"));
            }
            this.handshakingFuture = null;
        }
        return true;
    }

}
