package sexy.kostya.proto4j.transport.highlevel.packet;

import sexy.kostya.proto4j.exception.Proto4jException;
import sexy.kostya.proto4j.transport.highlevel.HighChannel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Created by k.shandurenko on 01.10.2020
 */
public class PacketHandler<C extends HighChannel> {

    private final Map<Class<? extends EnumeratedProto4jPacket>, List<BiConsumer<C, ? extends EnumeratedProto4jPacket>>> handlers = new HashMap<>();

    public <P extends EnumeratedProto4jPacket> void register(Class<P> packetClass, BiConsumer<C, P> handler) {
        this.handlers.computeIfAbsent(packetClass, pc -> new ArrayList<>()).add(handler);
    }

    public <P extends EnumeratedProto4jPacket> void unregisterAll(Class<P> packetClass) {
        this.handlers.remove(packetClass);
    }

    public <P extends EnumeratedProto4jPacket> void handle(C channel, P packet) {
        List<BiConsumer<C, ? extends EnumeratedProto4jPacket>> handlers = this.handlers.get(packet.getClass());
        if (handlers == null) {
            return;
        }
        handlers.forEach(handler -> {
            try {
                //noinspection unchecked
                ((BiConsumer<C, P>) handler).accept(channel, packet);
            } catch (Throwable t) {
                throw new Proto4jException("Could not handle packet " + packet.getClass().getSimpleName(), t);
            }
        });
    }

}
