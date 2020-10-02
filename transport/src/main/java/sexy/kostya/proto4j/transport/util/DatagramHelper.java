package sexy.kostya.proto4j.transport.util;

import io.netty.buffer.ByteBuf;

import java.util.zip.CRC32;

/**
 * Created by k.shandurenko on 29.09.2020
 */
public class DatagramHelper {

    public final static int    MAX_DATAGRAM_SIZE     = 508;
    public final static byte[] ZERO_LENGTH_ARRAY     = new byte[0];
    public final static long   RELIABILITY_THRESHOLD = 10L;

    /**
     * 2 - length
     * 1 - flags
     * 4 - sequence number
     * BODY
     * 4 - crc
     */
    public final static int HEADER_LENGTH = 2 + 4 + 1;
    public final static int CRC_LENGTH    = 4;

    public final static int MIN_SEQUENCE_NUMBER = 0;
    public final static int MAX_SEQUENCE_NUMBER = 2_000_000_000;

    public static boolean isValidSequenceNumber(int seq) {
        return seq >= MIN_SEQUENCE_NUMBER && seq <= MAX_SEQUENCE_NUMBER;
    }

    public static int getNextSequenceNumber(int seq) {
        return seq == MAX_SEQUENCE_NUMBER ? MIN_SEQUENCE_NUMBER : seq + 1;
    }

    public static int crc32(byte[] array, int offset, int length) {
        CRC32 crc32 = new CRC32();
        crc32.update(array, offset, length);
        return (int) crc32.getValue();
    }

    public static void log(String prefix, ByteBuf buffer) {
        StringBuilder sb = new StringBuilder();
        int index = buffer.readerIndex();
        while (buffer.readableBytes() > 0) {
            sb.append(String.format("0x%02X ", buffer.readByte()));
        }
        System.out.println(prefix + sb.toString().trim());
        buffer.readerIndex(index);
    }

}
