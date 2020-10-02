package sexy.kostya.proto4j.transport.attribute;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by k.shandurenko on 30.09.2020
 */
public class Attribute<V> {

    private final AtomicReference<V> reference = new AtomicReference<>();

    public V get() {
        return this.reference.get();
    }

    public void set(V value) {
        this.reference.set(value);
    }

}
