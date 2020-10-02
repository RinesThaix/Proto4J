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
import sexy.kostya.proto4j.transport.lowlevel.Proto4jServer;
import sexy.kostya.proto4j.transport.packet.Proto4jPacket;

import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Set;
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
            if (Handshake.processOnServerside(channel, packet.getBuffer())) {
                channel.handshaked = true;
                channel.setHandler(getHandlers(), p -> {
                    EnumeratedProto4jPacket enumeratedPacket = this.packetManager.readPacket(p.getBuffer());
                    switch (enumeratedPacket.getID()) {
                        case Packet1Ping.ID:
                            // nothing is here
                            break;
                        case Packet2Disconnect.ID:
                            disconnect(channel, false, null, null);
                            break;
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

    public Proto4jHighServer(int workerThreads, int handlerThreads) {
        this(LoggerFactory.getLogger("Proto4j HighServer"), workerThreads, handlerThreads);
        Thread thread = new Thread(() -> {
            Set<InetSocketAddress> toBeRemoved = new HashSet<>();
            while (true) {
                long current = System.currentTimeMillis();
                super.channel.getAll().values().forEach(channel -> {
                    if (!channel.isHandshaked()) {
                        return;
                    }
                    if (current - channel.getLastPacketReceived() > 10_000L) {
                        disconnect(channel, true, "Timed out", toBeRemoved);
                        return;
                    }
                    if (current - channel.getLastPacketReceived() > 1_000L || current - channel.getLastPacketSent() > 1_000L) {
                        channel.send(new Packet1Ping());
                    }
                });
                toBeRemoved.forEach(super.channel::remove);
                toBeRemoved.clear();
                try {
                    Thread.sleep(1_000L);
                } catch (InterruptedException ignored) {
                }
            }
        }, "Proto4j Ping Thread");
        thread.setDaemon(true);
        thread.start();
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
    public void stop() {
        super.channel.getAll().values().forEach(channel -> channel.send(new Packet2Disconnect("Server is stopping"), Proto4jPacket.Flag.UNRELIABLE));
        super.stop();
    }

    @Override
    protected boolean stop0() {
        if (!super.stop0()) {
            return false;
        }
        super.channel.clear();
        return true;
    }

    public void disconnect(C channel, String reason) {
        disconnect(channel, true, reason, null);
    }

    private void disconnect(C channel, boolean callback, String reason, Set<InetSocketAddress> toBeRemoved) {
        if (this.onDisconnect != null) {
            this.onDisconnect.accept(channel);
        }
        if (callback) {
            channel.send(new Packet2Disconnect(reason));
        }
        channel.active = false;
        if (toBeRemoved != null) {
            toBeRemoved.add(channel.getCodec().getAddress());
        } else {
            super.channel.remove(channel.getCodec().getAddress());
        }
    }
}
