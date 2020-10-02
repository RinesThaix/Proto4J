package sexy.kostya.proto4j.transport.util;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Created by k.shandurenko on 29.09.2020
 */
public class Recycler<T> {

    private final Supplier<T> generator;

    private final BlockingQueue<T> pool;

    private final ReentrantLock lock = new ReentrantLock();

    public Recycler(Supplier<T> generator) {
        this.generator = generator;
        this.pool = new LinkedBlockingQueue<>();
    }

    public T acquire() {
        T value = this.pool.poll();
        if (value == null) {
            this.lock.lock();
            try {
                value = this.generator.get();
            } finally {
                this.lock.unlock();
            }
        }
        return value;
    }

    public void recycle(T value) {
        this.pool.add(value);
    }

}
