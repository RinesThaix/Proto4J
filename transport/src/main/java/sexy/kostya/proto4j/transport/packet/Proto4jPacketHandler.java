package sexy.kostya.proto4j.transport.packet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sexy.kostya.proto4j.transport.Channel;

import java.util.concurrent.Executor;

/**
 * Created by k.shandurenko on 30.09.2020
 */
public abstract class Proto4jPacketHandler {

    private final static Logger LOGGER = LoggerFactory.getLogger("Proto4j Handler");

    private final Channel  channel;
    private final Executor executor;

    public Proto4jPacketHandler(Channel channel, Executor executor) {
        this.channel = channel;
        this.executor = executor;
    }

    public abstract void handle(Proto4jPacket packet);

    void handle0(Proto4jPacket packet, Runnable finalizer) {
        this.executor.execute(() -> {
            try {
                handle(packet);
            } catch (Exception e) {
                e.printStackTrace();
                LOGGER.warn("Packet handling caught an exception", e);
            } finally {
                finalizer.run();
            }
        });
    }

    protected Channel getChannel() {
        return this.channel;
    }

}
