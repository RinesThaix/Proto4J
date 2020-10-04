package sexy.kostya.proto4j.transport.highlevel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sexy.kostya.proto4j.commons.Proto4jProperties;
import sexy.kostya.proto4j.transport.highlevel.packet.CallbackProto4jPacket;
import sexy.kostya.proto4j.transport.highlevel.packet.EnumeratedProto4jPacket;
import sexy.kostya.proto4j.transport.highlevel.packet.PacketHandler;
import sexy.kostya.proto4j.transport.highlevel.packet.PacketManager;
import sexy.kostya.proto4j.transport.highlevel.packet.def.DefaultPacketManager;
import sexy.kostya.proto4j.transport.highlevel.packet.def.Packet1Ping;
import sexy.kostya.proto4j.transport.highlevel.packet.def.Packet2Disconnect;
import sexy.kostya.proto4j.transport.lowlevel.Proto4jServer;
import sexy.kostya.proto4j.transport.packet.Proto4jPacket;

import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Created by k.shandurenko on 30.09.2020
 */
public abstract class Proto4jHighServer<C extends HighChannel> extends Proto4jServer<C> {

    private       PacketManager     packetManager     = new DefaultPacketManager();
    private       PacketHandler<C>  packetHandler     = new PacketHandler<>();
    private final CallbacksRegistry callbacksRegistry = new CallbacksRegistry();

    private Consumer<C> onDisconnect;

    public Proto4jHighServer(Logger logger, int workerThreads, int handlerThreads) {
        super(logger, workerThreads, handlerThreads);
        super.setInitialPacketHandler((channel, packet) -> {
            CompletableFuture<Void> completed = new CompletableFuture<>();
            if (Handshake.processOnServerside(channel, packet.getBuffer(), completed)) {
                channel.handshaked = true;
                channel.setHandler(getHandlers(), p -> {
                    EnumeratedProto4jPacket enumeratedPacket = this.packetManager.readPacket(p.getBuffer());
                    getLogger().trace("Received {} from {}", enumeratedPacket.getClass().getSimpleName(), channel.getCodec().getAddress());
                    switch (enumeratedPacket.getID()) {
                        case Packet1Ping.ID:
                            // nothing is here
                            break;
                        case Packet2Disconnect.ID:
                            Packet2Disconnect casted = (Packet2Disconnect) enumeratedPacket;
                            if (handleCallbackPacket(casted)) {
                                break;
                            }
                            disconnect(channel, casted, null, null);
                            break;
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
            }
            completed.complete(null);
        });
        long receivedTimeout = Proto4jProperties.getProperty("highTimeout", 10_000L);
        long pingDelay       = Proto4jProperties.getProperty("highPingDelay", 1_000L);
        Thread thread = new Thread(() -> {
            Set<InetSocketAddress> toBeRemoved = new HashSet<>();
            while (true) {
                long current = System.currentTimeMillis();
                super.channel.getAll().values().forEach(channel -> {
                    if (!channel.isHandshaked() || !channel.isActive()) {
                        return;
                    }
                    if (current - channel.getLastPacketReceived() > receivedTimeout) {
                        disconnect(channel, null, "Timed out", toBeRemoved);
                        return;
                    }
                    if (current - channel.getLastPacketReceived() > pingDelay || current - channel.getLastPacketSent() > pingDelay) {
                        channel.send(new Packet1Ping());
                    }
                });
                toBeRemoved.forEach(super.channel::remove);
                toBeRemoved.clear();
                try {
                    Thread.sleep(pingDelay);
                } catch (InterruptedException ignored) {
                }
            }
        }, "Proto4j Ping Thread");
        thread.setDaemon(true);
        thread.start();
    }

    public Proto4jHighServer(int workerThreads, int handlerThreads) {
        this(LoggerFactory.getLogger("Proto4j HighServer"), workerThreads, handlerThreads);
    }

    private boolean handleCallbackPacket(CallbackProto4jPacket packet) {
        if (packet.getCallbackID() < 0) {
            packet.setCallbackID((short) -packet.getCallbackID());
            this.callbacksRegistry.responded(packet);
            return true;
        }
        return false;
    }

    public void setPacketManager(PacketManager packetManager) {
        this.packetManager = packetManager;
    }

    public PacketManager getPacketManager() {
        return packetManager;
    }

    public PacketHandler<C> getPacketHandler() {
        return packetHandler;
    }

    public void setPacketHandler(PacketHandler<C> packetHandler) {
        this.packetHandler = packetHandler;
    }

    public synchronized void addOnDisconnect(Consumer<C> onDisconnect) {
        if (this.onDisconnect == null) {
            this.onDisconnect = ch -> {
                try {
                    onDisconnect.accept(ch);
                } catch (Throwable t) {
                    getLogger().warn("Could not handle disconnection of channel", t);
                }
            };
        } else {
            Consumer<C> before = this.onDisconnect;
            this.onDisconnect = ch -> {
                before.accept(ch);
                try {
                    onDisconnect.accept(ch);
                } catch (Throwable t) {
                    getLogger().warn("Could not handle disconnection of channel", t);
                }
            };
        }
    }

    public CallbacksRegistry getCallbacksRegistry() {
        return callbacksRegistry;
    }

    @Override
    protected boolean shutdownInternally() {
        CompletionStage<Void> disconnection = null;
        for (C channel : super.channel.getAll().values()) {
            CompletionStage<CallbackProto4jPacket> stage = channel.sendWithCallback(new Packet2Disconnect("Server is stopping"));
            if (disconnection == null) {
                disconnection = stage.thenAccept(p -> {
                });
            } else {
                disconnection = disconnection.thenAcceptBoth(stage, (v, p) -> {
                });
            }
        }
        if (disconnection != null) {
            try {
                disconnection.toCompletableFuture().get(HighChannel.INITIAL_DELAY, TimeUnit.MILLISECONDS);
            } catch (Exception ignored) {
            }
        }
        if (!super.shutdownInternally()) {
            return false;
        }
        super.channel.clear();
        return true;
    }

    public void disconnect(C channel, String reason) {
        disconnect(channel, null, reason, null);
    }

    private void disconnect(C channel, Packet2Disconnect callback, String reason, Set<InetSocketAddress> toBeRemoved) {
        if (this.onDisconnect != null) {
            this.onDisconnect.accept(channel);
        }
        if (callback == null) {
            channel.send(new Packet2Disconnect(reason), Proto4jPacket.Flag.UNRELIABLE);
        } else {
            callback.respond(channel, callback, Proto4jPacket.Flag.UNRELIABLE);
        }
        channel.active = false;
        if (toBeRemoved != null) {
            toBeRemoved.add(channel.getCodec().getAddress());
        } else {
            super.channel.remove(channel.getCodec().getAddress());
        }
    }
}
