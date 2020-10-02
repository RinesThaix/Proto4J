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

/**
 * Created by k.shandurenko on 01.10.2020
 */
public abstract class Proto4jHighClient<C extends HighChannel> extends Proto4jClient<C> {

    private       PacketManager     packetManager     = new DefaultPacketManager();
    private       PacketHandler<C>  packetHandler     = new PacketHandler<>();
    private final CallbacksRegistry callbacksRegistry = new CallbacksRegistry();

    private CompletableFuture<Void> handshakingFuture;

    public Proto4jHighClient(Logger logger, int workerThreads, int handlerThreads) {
        super(logger, workerThreads, handlerThreads);
        super.setInitialPacketHandler((channel, packet) -> {
            if (Handshake.processOnClientside(channel, packet.getBuffer())) {
                channel.handshaked = true;
                this.handshakingFuture.complete(null);
                channel.setHandler(getHandlers(), p -> {
                    EnumeratedProto4jPacket enumeratedPacket = this.packetManager.readPacket(p.getBuffer());
                    switch (enumeratedPacket.getID()) {
                        case Packet1Ping.ID:
                            channel.send(new Packet1Ping());
                            break;
                        case Packet2Disconnect.ID: {
                            Packet2Disconnect casted = (Packet2Disconnect) enumeratedPacket;
                            if (casted.getReason() == null) {
                                getLogger().info("Disconnected by server");
                            } else {
                                getLogger().info("Disconnected by server: {}", casted.getReason());
                            }
                            super.stop();
                            break;
                        }
                        default:
                            if (enumeratedPacket instanceof CallbackProto4jPacket) {
                                CallbackProto4jPacket casted = (CallbackProto4jPacket) enumeratedPacket;
                                if (casted.getCallbackID() < 0) {
                                    casted.setCallbackID((short) -casted.getCallbackID());
                                    this.callbacksRegistry.responded(casted);
                                    return;
                                }
                            }
                            this.packetHandler.handle(channel, enumeratedPacket);
                            break;
                    }
                });
            }
        });
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
    public void stop() {
        getChannel().send(new Packet2Disconnect(), Proto4jPacket.Flag.UNRELIABLE);
        super.stop();
    }

    @Override
    protected boolean stop0() {
        if (!super.stop0()) {
            return false;
        }
        if (this.handshakingFuture != null) {
            if (!this.handshakingFuture.isDone()) {
                this.handshakingFuture.completeExceptionally(new Exception("Disconnected"));
            }
            this.handshakingFuture = null;
        }
        C channel = getChannel();
        if (channel != null) {
            channel.active = false;
        }
        return true;
    }
}
