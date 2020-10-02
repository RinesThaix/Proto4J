package sexy.kostya.proto4j.transport;

import sexy.kostya.proto4j.transport.buffer.Buffer;
import sexy.kostya.proto4j.transport.highlevel.base.BaseProto4jHighClient;
import sexy.kostya.proto4j.transport.highlevel.base.BaseProto4jHighServer;
import sexy.kostya.proto4j.transport.highlevel.packet.CallbackProto4jPacket;
import sexy.kostya.proto4j.transport.highlevel.packet.EnumeratedProto4jPacket;
import sexy.kostya.proto4j.transport.highlevel.packet.PacketManager;

import java.util.concurrent.ExecutionException;

/**
 * Created by k.shandurenko on 30.09.2020
 */
public class TheTest {

    public static void main(String[] args) throws ExecutionException, InterruptedException {
        BaseProto4jHighServer server = new BaseProto4jHighServer(3, 3);
        setupPacketManager(server.getPacketManager());
        server.getPacketHandler().register(TestPacket.class, (channel, packet) -> {
            System.out.println("C -> S: " + packet.value);
            packet.value = "Hello there!";
            packet.respond(channel, packet);
        });
        server.start(6775).get();

        BaseProto4jHighClient client = new BaseProto4jHighClient(3, 3);
        setupPacketManager(client.getPacketManager());
        client.connect("127.0.0.1", 6775).get();
        TestPacket packet = (TestPacket) client.getChannel().sendWithCallback(new TestPacket("Hello!")).get();
        System.out.println("S -> C: " + packet.value);
    }

    private static void setupPacketManager(PacketManager manager) {
        manager.register(TestPacket::new);
    }

    public static class TestPacket extends CallbackProto4jPacket {

        private String value;

        public TestPacket() {
        }

        public TestPacket(String value) {
            this.value = value;
        }

        @Override
        public int getID() {
            return 1;
        }

        @Override
        public void write(Buffer buffer) {
            buffer.writeString(this.value);
        }

        @Override
        public void read(Buffer buffer) {
            this.value = buffer.readString();
        }

    }

}
