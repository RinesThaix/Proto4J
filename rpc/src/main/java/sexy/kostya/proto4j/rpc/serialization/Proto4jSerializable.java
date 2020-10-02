package sexy.kostya.proto4j.rpc.serialization;

import sexy.kostya.proto4j.transport.buffer.Buffer;

/**
 * Created by k.shandurenko on 30.09.2020
 */
public interface Proto4jSerializable {

    void write(Buffer buffer);

    void read(Buffer buffer);

}
