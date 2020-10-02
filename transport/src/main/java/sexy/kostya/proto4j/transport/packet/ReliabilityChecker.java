package sexy.kostya.proto4j.transport.packet;

import sexy.kostya.proto4j.transport.buffer.Buffer;
import sexy.kostya.proto4j.transport.buffer.BufferImpl;
import sexy.kostya.proto4j.transport.util.DatagramHelper;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by k.shandurenko on 30.09.2020
 */
class ReliabilityChecker {

    private final Map<Integer, ConfirmationAwaitingPacket>             awaitingPackets        = new ConcurrentHashMap<>();
    private final Map<Integer, Map<Short, ConfirmationAwaitingPacket>> awaitingPartialPackets = new ConcurrentHashMap<>();

    ReliabilityChecker(PacketCodec codec) {
        Thread thread = new Thread(() -> {
            while (true) {
                long current = System.currentTimeMillis();
                this.awaitingPackets.forEach((sn, packet) -> {
                    if (current - packet.time > DatagramHelper.RELIABILITY_THRESHOLD) {
                        codec.getEncoder().send(((BufferImpl) packet.buffer).getHandle().array());
                        packet.time = current;
                    }
                });
                this.awaitingPartialPackets.forEach((sn, map) -> {
                    map.forEach((id, packet) -> {
                        if (current - packet.time > DatagramHelper.RELIABILITY_THRESHOLD) {
                            codec.getEncoder().send(((BufferImpl) packet.buffer).getHandle().array());
                            packet.time = current;
                        }
                    });
                });
                try {
                    Thread.sleep(DatagramHelper.RELIABILITY_THRESHOLD >> 2);
                } catch (InterruptedException ignored) {
                }
            }
        }, "Proto4j Reliability Thread");
        thread.setDaemon(true);
        thread.start();
    }

    void remove(int sequenceNumber) {
        ConfirmationAwaitingPacket packet = this.awaitingPackets.remove(sequenceNumber);
        if (packet != null) {
            packet.buffer.release();
        }
    }

    void remove(int sequenceNumber, short partialIndex) {
        Map<Short, ConfirmationAwaitingPacket> map = this.awaitingPartialPackets.get(sequenceNumber);
        if (map == null) {
            return;
        }
        ConfirmationAwaitingPacket packet = map.remove(partialIndex);
        if (packet != null) {
            packet.buffer.release();
            if (map.isEmpty()) {
                this.awaitingPartialPackets.remove(sequenceNumber);
            }
        }
    }

    class ConfirmationAwaitingPacket {

        private       long   time;
        private final int    sequenceNumber;
        private final short  partiteIndex;
        private final Buffer buffer;

        ConfirmationAwaitingPacket(int sequenceNumber, short partiteIndex, Buffer buffer) {
            this.time = System.currentTimeMillis();
            this.sequenceNumber = sequenceNumber;
            this.partiteIndex = partiteIndex;
            this.buffer = buffer;
        }

        void register() {
            if (this.partiteIndex == -1) {
                awaitingPackets.put(this.sequenceNumber, this);
            } else {
                awaitingPartialPackets.computeIfAbsent(this.sequenceNumber, sn -> new ConcurrentHashMap<>()).put(this.partiteIndex, this);
            }
        }
    }

}
