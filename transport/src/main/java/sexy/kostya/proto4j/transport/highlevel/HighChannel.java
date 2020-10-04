package sexy.kostya.proto4j.transport.highlevel;

import sexy.kostya.proto4j.commons.Proto4jProperties;
import sexy.kostya.proto4j.transport.Channel;
import sexy.kostya.proto4j.transport.buffer.Buffer;
import sexy.kostya.proto4j.transport.highlevel.packet.CallbackProto4jPacket;
import sexy.kostya.proto4j.transport.highlevel.packet.EnumeratedProto4jPacket;
import sexy.kostya.proto4j.transport.packet.PacketCodec;
import sexy.kostya.proto4j.transport.packet.Proto4jPacket;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/**
 * Created by k.shandurenko on 01.10.2020
 */
public class HighChannel extends Channel {

    public final static long INITIAL_DELAY = Proto4jProperties.getProperty("callbacksInitialDelay", 500L);

    private final CallbacksRegistry callbacksRegistry;

    boolean handshaked;
    boolean active = true;
    private volatile long lastPacketReceived;
    private volatile long lastPacketSent;

    public HighChannel(CallbacksRegistry callbacksRegistry, PacketCodec codec) {
        super(codec);
        this.callbacksRegistry = callbacksRegistry;
    }

    public boolean isHandshaked() {
        return handshaked;
    }

    public boolean isActive() {
        return active;
    }

    public long getLastPacketReceived() {
        return lastPacketReceived;
    }

    public long getLastPacketSent() {
        return lastPacketSent;
    }

    @Override
    public boolean recv(Buffer buffer) {
        this.lastPacketReceived = System.currentTimeMillis();
        return super.recv(buffer);
    }

    @Override
    public void send(Proto4jPacket packet) {
        this.lastPacketSent = System.currentTimeMillis();
        super.send(packet);
    }

    public void send(EnumeratedProto4jPacket packet) {
        send(packet, 0);
    }

    public void send(EnumeratedProto4jPacket packet, int flags) {
        Buffer buffer = Buffer.newBuffer();
        buffer.writeVarInt(packet.getID());
        packet.write0(buffer);
        send((byte) flags, buffer);
    }

    public CompletionStage<CallbackProto4jPacket> sendWithCallback(CallbackProto4jPacket packet) {
        return sendWithCallback(packet, INITIAL_DELAY, TimeUnit.MILLISECONDS);
    }

    public CompletionStage<CallbackProto4jPacket> sendWithCallback(CallbackProto4jPacket packet, long time, TimeUnit timeUnit) {
        CompletableFuture<CallbackProto4jPacket> future = new CompletableFuture<>();
        this.callbacksRegistry.awaiting(packet, future, timeUnit, time);
        send(packet);
        return future;
    }

}
