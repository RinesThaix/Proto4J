package sexy.kostya.proto4j.transport.highlevel;

import com.google.common.util.concurrent.SettableFuture;
import sexy.kostya.proto4j.transport.highlevel.packet.CallbackProto4jPacket;

import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Created by k.shandurenko on 01.10.2020
 */
public class CallbacksRegistry {

    private final Map<Short, CallbackData> callbacks = new ConcurrentHashMap<>();

    public CallbacksRegistry() {
        Thread thread = new Thread(() -> {
            Set<Short> toBeRemoved = new HashSet<>();
            while (true) {
                long current = System.currentTimeMillis();
                this.callbacks.forEach((id, data) -> {
                    if (current > data.maxTime) {
                        toBeRemoved.add(id);
                        data.future.setException(new TimeoutException());
                    }
                });
                toBeRemoved.forEach(this.callbacks::remove);
                try {
                    Thread.sleep(100L);
                } catch (InterruptedException ignored) {
                }
            }
        }, "Proto4j Callbacks Thread");
        thread.setDaemon(true);
        thread.start();
    }

    public void awaiting(CallbackProto4jPacket packet, SettableFuture<CallbackProto4jPacket> future, TimeUnit timeUnit, long time) {
        short  id;
        Random random = ThreadLocalRandom.current();
        do {
            id = (short) random.nextInt(Short.MAX_VALUE - 10);
        } while (this.callbacks.containsKey(id));
        packet.setCallbackID(id);
        CallbackData data = new CallbackData(future, System.currentTimeMillis() + timeUnit.toMillis(time));
        this.callbacks.put(id, data);
    }

    public void responded(CallbackProto4jPacket packet) {
        CallbackData data = this.callbacks.remove(packet.getCallbackID());
        if (data != null) {
            data.future.set(packet);
        }
    }

    private static class CallbackData {

        private final SettableFuture<CallbackProto4jPacket> future;
        private final long                                  maxTime;

        public CallbackData(SettableFuture<CallbackProto4jPacket> future, long maxTime) {
            this.future = future;
            this.maxTime = maxTime;
        }
    }

}
