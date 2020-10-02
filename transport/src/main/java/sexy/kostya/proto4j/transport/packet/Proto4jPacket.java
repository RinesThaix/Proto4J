package sexy.kostya.proto4j.transport.packet;

import com.google.common.base.Preconditions;
import sexy.kostya.proto4j.transport.buffer.Buffer;

/**
 * Created by k.shandurenko on 30.09.2020
 */
public class Proto4jPacket {

    private int    sequenceNumber = -1;
    private byte   flags;
    private Buffer buffer;

    public Proto4jPacket(byte flags, Buffer buffer) {
        this(-1, flags, buffer);
    }

    public Proto4jPacket(int sequenceNumber, byte flags, Buffer buffer) {
        this.sequenceNumber = sequenceNumber;
        this.flags = flags;
        this.buffer = buffer;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    void setSequenceNumber(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public byte getFlags() {
        return flags;
    }

    public Buffer getBuffer() {
        return buffer;
    }

    public static class Flag {
        public final static byte CONFIRMATION  = 0x01; // indicates that some packet was received
        public final static byte PARTIAL       = 0x02; // is a part of a large packet or a confirmation about part
        public final static byte UNORDERED     = 0x04; // order is not guaranteed, but executes faster
        public final static byte UNSIGNED_BODY = 0x08; // only header is signed: therefore, data may be corrupted
        public final static byte UNRELIABLE    = 0x10; // explicitly mark that confirmation is not required
        public final static byte INDIVISIBLE   = 0x20; // explicitly mark that this packet can't be split into parts

        public static void validate(byte flags) {
            if ((flags & CONFIRMATION) != 0) {
                Preconditions.checkState((flags & UNRELIABLE) != 0, "Confirmation packet must be unreliable");
                Preconditions.checkState((flags & INDIVISIBLE) != 0, "Confirmation packet must be indivisible");
            } else if ((flags & PARTIAL) != 0) {
                Preconditions.checkState((flags & UNSIGNED_BODY) == 0, "Partial packet can't be with unsigned body");
                Preconditions.checkState((flags & UNRELIABLE) == 0, "Partial packet can't be unreliable");
                Preconditions.checkState((flags & INDIVISIBLE) == 0, "Partial packet can't be indivisible");
            }
        }
    }

}
