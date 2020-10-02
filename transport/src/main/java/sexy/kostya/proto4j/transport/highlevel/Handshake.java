package sexy.kostya.proto4j.transport.highlevel;

import sexy.kostya.proto4j.exception.Proto4jHandshakingException;
import sexy.kostya.proto4j.transport.Channel;
import sexy.kostya.proto4j.transport.buffer.Buffer;

/**
 * Created by k.shandurenko on 30.09.2020
 */
public class Handshake {

    private final static long   MAGIC         = 0xD3ADC0DE007L;
    private final static String ATTRIBUTE_KEY = "_hst";

    public static void initOnClientside(Channel channel) {
        long   time   = System.currentTimeMillis();
        Buffer buffer = Buffer.newBuffer(16);
        buffer.writeLong(MAGIC);
        buffer.writeLong(time);
        channel.getAttributes().set(ATTRIBUTE_KEY, time);
        channel.send(buffer);
    }

    public static boolean processOnClientside(Channel channel, Buffer in) {
        long first  = in.readLong();
        long second = in.readLong();
        if (first == MAGIC) {
            Long clientTime = channel.getAttributes().get(ATTRIBUTE_KEY);
            if (clientTime == null) {
                throw new Proto4jHandshakingException("Handshaking time not stored in channel attributes");
            }
            if (second != clientTime) {
                throw new Proto4jHandshakingException("Handshaking time does not match");
            }
            long serverTime = in.readLong();
            channel.getAttributes().set(ATTRIBUTE_KEY, serverTime);
            Buffer out = Buffer.newBuffer(16);
            out.writeLong(serverTime);
            out.writeLong(MAGIC);
            channel.send(out);
            return false;
        } else if (second == MAGIC) {
            Long serverTime = channel.getAttributes().remove(ATTRIBUTE_KEY);
            if (serverTime == null) {
                throw new Proto4jHandshakingException("Handshaking time not stored in channel attributes");
            }
            if (first != serverTime) {
                throw new Proto4jHandshakingException("Handshaking time does not match");
            }
            return true;
        } else {
            throw new Proto4jHandshakingException("Not a handshaking packet");
        }
    }

    public static boolean processOnServerside(Channel channel, Buffer in) {
        long first  = in.readLong();
        long second = in.readLong();
        if (first == MAGIC) {
            long   time = System.currentTimeMillis();
            Buffer out  = Buffer.newBuffer(24);
            out.writeLong(MAGIC);
            out.writeLong(second);
            out.writeLong(time);
            channel.getAttributes().set(ATTRIBUTE_KEY, time);
            channel.send(out);
            return false;
        } else if (second == MAGIC) {
            Long serverTime = channel.getAttributes().remove(ATTRIBUTE_KEY);
            if (serverTime == null) {
                throw new Proto4jHandshakingException("Handshaking time not stored in channel attributes");
            }
            if (first != serverTime) {
                throw new Proto4jHandshakingException("Handshaking time does not match");
            }
            Buffer out = Buffer.newBuffer(16);
            out.writeLong(serverTime);
            out.writeLong(MAGIC);
            channel.send(out);
            return true;
        } else {
            throw new Proto4jHandshakingException("Not a handshaking packet");
        }
    }

}
