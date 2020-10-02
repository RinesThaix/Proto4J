package sexy.kostya.proto4j.transport.packet;

import com.google.common.base.Preconditions;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import sexy.kostya.proto4j.transport.buffer.Buffer;
import sexy.kostya.proto4j.transport.buffer.BufferImpl;
import sexy.kostya.proto4j.transport.util.DatagramHelper;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by k.shandurenko on 30.09.2020
 */
public class PacketDecoder {

    private final PacketCodec codec;

    private final AtomicInteger                    sequence = new AtomicInteger();
    private final Map<Integer, Proto4jPacket>      order    = new ConcurrentHashMap<>();
    private final Map<Integer, Map<Short, Buffer>> partites = new ConcurrentHashMap<>();

    PacketDecoder(PacketCodec codec) {
        this.codec = codec;
    }

    public void read(Buffer buffer, Proto4jPacketHandler handler) {
        if (buffer.readableBytes() < 2) {
            buffer.release();
            return;
        }
        BufferImpl bufferImpl      = (BufferImpl) buffer;
        ByteBuf    handle          = bufferImpl.getHandle();
        int        initialPosition = handle.readerIndex();
        short      length          = buffer.readShort();
        if (length != buffer.readableBytes() + 2) {
            buffer.release();
            return;
        }
        int sequenceNumber = buffer.readInt();
        if (!DatagramHelper.isValidSequenceNumber(sequenceNumber)) {
            buffer.release();
            return;
        }
        byte flags = buffer.readByte();
        length = (short) (buffer.readableBytes() - 4); // length of the body
        handle.markReaderIndex();
        handle.skipBytes(length);
        int crc = buffer.readInt();
        if ((flags & Proto4jPacket.Flag.UNSIGNED_BODY) == 0) {
            if (crc != DatagramHelper.crc32(handle.array(), initialPosition, length + DatagramHelper.HEADER_LENGTH)) {
                buffer.release();
                return;
            }
        } else {
            if (crc != DatagramHelper.crc32(handle.array(), initialPosition, DatagramHelper.HEADER_LENGTH)) {
                buffer.release();
                return;
            }
        }
        handle.resetReaderIndex();
        handle = handle.slice(handle.readerIndex(), length);
        bufferImpl.setHandle(handle);

        if ((flags & Proto4jPacket.Flag.CONFIRMATION) != 0) {
            if ((flags & Proto4jPacket.Flag.PARTIAL) != 0) {
                short index = buffer.readShort();
                this.codec.getReliabilityChecker().remove(sequenceNumber, index);
            } else {
                this.codec.getReliabilityChecker().remove(sequenceNumber);
            }
            buffer.release();
            return;
        }

        if ((flags & Proto4jPacket.Flag.PARTIAL) != 0) {
            short index = buffer.readShort();
            short total = buffer.readShort();
            handle = handle.slice(handle.readerIndex(), length - 4);
            bufferImpl.setHandle(handle);
            Map<Short, Buffer> part = this.partites.computeIfAbsent(sequenceNumber, sn -> new ConcurrentHashMap<>());
            this.codec.getEncoder().writeConfirmationPartite(sequenceNumber, index);
            if (part.containsKey(index)) {
                buffer.release();
                return;
            }
            part.put(index, buffer);
            if (part.size() < total) {
                return;
            }
            int sumLength = 0;
            for (short i = 0; i < total; ++i) {
                Buffer buf = part.get(i);
                Preconditions.checkState(buf != null, "For packet %s part %s is not present", sequenceNumber, i);
                sumLength += buf.readableBytes();
            }
            this.partites.remove(sequenceNumber);
            ByteBuf newHandle = Unpooled.buffer(sumLength, sumLength);
            for (short i = 0; i < total; ++i) {
                Buffer buf = part.get(i);
                newHandle.writeBytes(((BufferImpl) buf).getHandle());
                if (buf != buffer) {
                    buf.release();
                }
            }
            bufferImpl.setHandle(newHandle);
            flags &= ~Proto4jPacket.Flag.PARTIAL;
        } else {
            bufferImpl.setHandle(handle.slice(handle.readerIndex(), length));
            if ((flags & Proto4jPacket.Flag.UNRELIABLE) == 0) {
                this.codec.getEncoder().writeConfirmation(sequenceNumber);
            }
        }
        Proto4jPacket packet = new Proto4jPacket(sequenceNumber, flags, buffer);
        if ((flags & Proto4jPacket.Flag.UNORDERED) != 0) {
            handle(packet, handler);
            return;
        }
        if (this.sequence.get() == sequenceNumber) {
            handle(packet, handler);
        } else {
            this.order.put(sequenceNumber, packet);
        }
    }

    private void handle(Proto4jPacket packet, Proto4jPacketHandler handler) {
        if (handler != null) {
            handler.handle0(packet, () -> finalize(packet, handler));
        } else {
            finalize(packet, null);
        }
    }

    private void finalize(Proto4jPacket packet, Proto4jPacketHandler handler) {
        packet.getBuffer().release();
        int nextSequenceNumber = DatagramHelper.getNextSequenceNumber(packet.getSequenceNumber());
        this.sequence.set(nextSequenceNumber);
        Proto4jPacket nextPacket = this.order.remove(nextSequenceNumber);
        if (nextPacket != null) {
            handle(nextPacket, handler);
        }
    }

}
