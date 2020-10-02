package sexy.kostya.proto4j.transport;

import sexy.kostya.proto4j.transport.attribute.AttributeMap;
import sexy.kostya.proto4j.transport.buffer.Buffer;
import sexy.kostya.proto4j.transport.packet.PacketCodec;
import sexy.kostya.proto4j.transport.packet.Proto4jPacket;
import sexy.kostya.proto4j.transport.packet.Proto4jPacketHandler;

import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Created by k.shandurenko on 30.09.2020
 */
public class Channel {

    private final PacketCodec          codec;
    private final AttributeMap         attributes = new AttributeMap();
    private       Proto4jPacketHandler handler;

    public Channel(PacketCodec codec) {
        this.codec = codec;
    }

    public AttributeMap getAttributes() {
        return this.attributes;
    }

    public void setHandler(Executor executor, Consumer<Proto4jPacket> handler) {
        this.handler = new Proto4jPacketHandler(this, executor) {
            @Override
            public void handle(Proto4jPacket packet) {
                handler.accept(packet);
            }
        };
    }

    public void recv(Buffer buffer) {
        this.codec.getDecoder().read(buffer, this.handler);
    }

    public void send(Buffer buffer) {
        send((byte) 0, buffer);
    }

    public void send(byte flags, Buffer buffer) {
        send(new Proto4jPacket(flags, buffer));
    }

    public void send(Proto4jPacket packet) {
        this.codec.getEncoder().write(packet);
    }

    public PacketCodec getCodec() {
        return this.codec;
    }

}
