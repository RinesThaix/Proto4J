package sexy.kostya.proto4j.transport.packet;

import java.net.DatagramSocket;
import java.net.InetSocketAddress;

/**
 * Created by k.shandurenko on 30.09.2020
 */
public class PacketCodec {

    private final DatagramSocket     socket;
    private final InetSocketAddress  address;
    private final PacketEncoder      encoder;
    private final PacketDecoder      decoder;
    private final ReliabilityChecker reliabilityChecker;

    public PacketCodec(DatagramSocket socket, InetSocketAddress address) {
        this.socket = socket;
        this.address = address;
        this.encoder = new PacketEncoder(this);
        this.decoder = new PacketDecoder(this);
        this.reliabilityChecker = new ReliabilityChecker(this);
    }

    public DatagramSocket getSocket() {
        return this.socket;
    }

    public InetSocketAddress getAddress() {
        return this.address;
    }

    public PacketEncoder getEncoder() {
        return this.encoder;
    }

    public PacketDecoder getDecoder() {
        return this.decoder;
    }

    public ReliabilityChecker getReliabilityChecker() {
        return this.reliabilityChecker;
    }
}
