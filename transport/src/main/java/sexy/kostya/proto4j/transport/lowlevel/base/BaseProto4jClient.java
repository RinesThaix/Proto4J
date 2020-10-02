package sexy.kostya.proto4j.transport.lowlevel.base;

import org.slf4j.Logger;
import sexy.kostya.proto4j.transport.Channel;
import sexy.kostya.proto4j.transport.lowlevel.Proto4jClient;
import sexy.kostya.proto4j.transport.packet.PacketCodec;

/**
 * Created by k.shandurenko on 01.10.2020
 */
public class BaseProto4jClient extends Proto4jClient<Channel> {

    public BaseProto4jClient(Logger logger, int workerThreads, int handlerThreads) {
        super(logger, workerThreads, handlerThreads);
    }

    public BaseProto4jClient(int workerThreads, int handlerThreads) {
        super(workerThreads, handlerThreads);
    }

    @Override
    public Channel createChannel(PacketCodec codec) {
        return new Channel(codec);
    }
}
