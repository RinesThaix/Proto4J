package sexy.kostya.proto4j.transport.highlevel.base;

import org.slf4j.Logger;
import sexy.kostya.proto4j.transport.highlevel.HighChannel;
import sexy.kostya.proto4j.transport.highlevel.Proto4jHighServer;
import sexy.kostya.proto4j.transport.packet.PacketCodec;

/**
 * Created by k.shandurenko on 01.10.2020
 */
public class BaseProto4jHighServer extends Proto4jHighServer<HighChannel> {

    public BaseProto4jHighServer(Logger logger, int workerThreads, int handlerThreads) {
        super(logger, workerThreads, handlerThreads);
    }

    public BaseProto4jHighServer(int workerThreads, int handlerThreads) {
        super(workerThreads, handlerThreads);
    }

    @Override
    public HighChannel createChannel(PacketCodec codec) {
        return new HighChannel(getCallbacksRegistry(), codec);
    }

}
