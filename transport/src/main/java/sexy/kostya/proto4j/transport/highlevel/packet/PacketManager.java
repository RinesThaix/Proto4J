package sexy.kostya.proto4j.transport.highlevel.packet;

import sexy.kostya.proto4j.transport.buffer.Buffer;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Created by k.shandurenko on 01.10.2020
 */
public class PacketManager {

    private final Map<Integer, Supplier<? extends EnumeratedProto4jPacket>> generators = new HashMap<>();

    @SuppressWarnings("unchecked")
    public <P extends EnumeratedProto4jPacket> P generate(int id) {
        return (P) this.generators.get(id).get();
    }

    public void register(Supplier<? extends EnumeratedProto4jPacket>... generators) {
        for (Supplier<? extends EnumeratedProto4jPacket> generator : generators) {
            this.generators.put(generator.get().getID(), generator);
        }
    }

    public <P extends EnumeratedProto4jPacket> P readPacket(Buffer buffer) {
        P packet = generate(buffer.readVarInt());
        packet.read0(buffer);
        return packet;
    }

}
