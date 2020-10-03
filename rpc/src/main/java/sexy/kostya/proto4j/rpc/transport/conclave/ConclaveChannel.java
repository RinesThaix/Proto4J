package sexy.kostya.proto4j.rpc.transport.conclave;

import sexy.kostya.proto4j.transport.highlevel.CallbacksRegistry;
import sexy.kostya.proto4j.transport.highlevel.HighChannel;
import sexy.kostya.proto4j.transport.packet.PacketCodec;

import java.util.Objects;

/**
 * Created by k.shandurenko on 03.10.2020
 */
public class ConclaveChannel extends HighChannel {

    private final int     id;
    private       boolean server;

    public ConclaveChannel(int id, CallbacksRegistry callbacksRegistry, PacketCodec codec) {
        super(callbacksRegistry, codec);
        this.id = id;
    }

    public int getId() {
        return this.id;
    }

    public boolean isServer() {
        return this.server;
    }

    void setServer(boolean server) {
        this.server = server;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ConclaveChannel that = (ConclaveChannel) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
