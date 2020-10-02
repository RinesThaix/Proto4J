package sexy.kostya.proto4j.transport.packet;

import com.google.common.base.Preconditions;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import sexy.kostya.proto4j.exception.Proto4jException;
import sexy.kostya.proto4j.transport.buffer.Buffer;
import sexy.kostya.proto4j.transport.buffer.BufferImpl;
import sexy.kostya.proto4j.transport.util.DatagramHelper;

import java.io.IOException;
import java.net.DatagramPacket;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by k.shandurenko on 30.09.2020
 */
public class PacketEncoder {

    private final PacketCodec codec;

    private final AtomicInteger sequence = new AtomicInteger();

    PacketEncoder(PacketCodec codec) {
        this.codec = codec;
    }

    public void write(Proto4jPacket packet) {
        if (packet.getSequenceNumber() == -1) {
            packet.setSequenceNumber(this.sequence.getAndUpdate(DatagramHelper::getNextSequenceNumber));
        }
        BufferImpl buffer = (BufferImpl) packet.getBuffer();
        ByteBuf    handle = buffer.getHandle();
        write(packet.getSequenceNumber(), packet.getFlags(), handle, 0, handle.writerIndex());
        buffer.release();
    }

    private void write(int sequenceNumber, byte flags, ByteBuf handle, int offset, int length) {
        short bodyLength   = (short) length;
        short packetLength = (short) (bodyLength + DatagramHelper.HEADER_LENGTH + DatagramHelper.CRC_LENGTH);
        if (packetLength > DatagramHelper.MAX_DATAGRAM_SIZE) {
            Preconditions.checkState((flags & Proto4jPacket.Flag.INDIVISIBLE) == 0, "The packet is too huge, but indivisible: it can't be sent");
            flags |= Proto4jPacket.Flag.PARTIAL;
            int   capacity        = DatagramHelper.MAX_DATAGRAM_SIZE - DatagramHelper.HEADER_LENGTH - DatagramHelper.CRC_LENGTH - 4;
            short total           = (short) Math.ceil((float) bodyLength / capacity);
            short extraBodyLength = (short) (bodyLength + 4 * total);
            int   extraCapacity   = capacity + 4;
            while (true) {
                short newTotal = (short) Math.ceil((float) extraBodyLength / extraCapacity);
                if (newTotal == total) {
                    break;
                }
                extraBodyLength += 4 * (newTotal - total);
                total = newTotal;
            }
            for (short i = 0; i < total; ++i) {
                int len;
                if (i == total - 1) {
                    len = bodyLength - capacity * (total - 1);
                } else {
                    len = capacity;
                }
                write0(sequenceNumber, flags, handle, offset, len, i, total);
                offset += capacity;
            }
        } else {
            write0(sequenceNumber, flags, handle, offset, length, (short) 0, (short) 0);
        }
    }

    private void write0(int sequenceNumber, byte flags, ByteBuf handle, int offset, int length, short partiteIndex, short partiteTotal) {
        Proto4jPacket.Flag.validate(flags);
        short bodyLength = (short) length;
        if (partiteTotal != 0) {
            bodyLength += 4;
        }
        short   packetLength = (short) (bodyLength + DatagramHelper.HEADER_LENGTH + DatagramHelper.CRC_LENGTH);
        ByteBuf newHandle    = Unpooled.buffer(packetLength, packetLength);
        Buffer  newBuffer    = Buffer.wrap(newHandle);
        newBuffer.writeShort(packetLength);
        newBuffer.writeInt(sequenceNumber);
        newBuffer.writeByte(flags);
        if (partiteTotal != 0) {
            newBuffer.writeShort(partiteIndex);
            newBuffer.writeShort(partiteTotal);
        }
        if (length > 0) {
            if (offset != 0 || length != handle.readableBytes()) {
                handle = handle.slice(offset, length);
            }
            newHandle.writeBytes(handle);
        }
        int crc;
        if ((flags & Proto4jPacket.Flag.UNSIGNED_BODY) == 0) {
            crc = DatagramHelper.crc32(newHandle.array(), 0, newHandle.writerIndex());
        } else {
            crc = DatagramHelper.crc32(newHandle.array(), 0, DatagramHelper.HEADER_LENGTH);
        }
        newBuffer.writeInt(crc);
        if ((flags & Proto4jPacket.Flag.UNRELIABLE) == 0) {
            this.codec.getReliabilityChecker().new ConfirmationAwaitingPacket(
                    sequenceNumber,
                    partiteTotal == 0 ? -1 : partiteIndex,
                    newBuffer
            ).register();
            send(newHandle.array());
        } else {
            send(newHandle.array());
            newBuffer.release();
        }
    }

    void send(byte[] array) {
        DatagramPacket packet = new DatagramPacket(array, array.length, this.codec.getAddress());
        try {
            this.codec.getSocket().send(packet);
        } catch (IOException e) {
            throw new Proto4jException("Could not send packet", e);
        }
    }

    void writeConfirmation(int sequenceNumber) {
        Proto4jPacket packet = new Proto4jPacket(
                sequenceNumber,
                (byte) (Proto4jPacket.Flag.CONFIRMATION | Proto4jPacket.Flag.UNRELIABLE | Proto4jPacket.Flag.INDIVISIBLE),
                Buffer.wrap(DatagramHelper.ZERO_LENGTH_ARRAY)
        );

        write(packet);
    }

    void writeConfirmationPartite(int sequenceNumber, short index) {
        Buffer buffer = Buffer.newBuffer(2);
        buffer.writeShort(index);

        Proto4jPacket packet = new Proto4jPacket(
                sequenceNumber,
                (byte) (Proto4jPacket.Flag.CONFIRMATION | Proto4jPacket.Flag.UNRELIABLE | Proto4jPacket.Flag.INDIVISIBLE | Proto4jPacket.Flag.PARTIAL),
                buffer
        );

        write(packet);
    }

}
