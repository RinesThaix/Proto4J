package sexy.kostya.proto4j.transport.attribute;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by k.shandurenko on 30.09.2020
 */
public class AttributeMap {

    private final Map<String, Attribute<?>> attributes = new ConcurrentHashMap<>();

    public boolean has(String key) {
        return this.attributes.containsKey(key);
    }

    public <V> V remove(String key) {
        Attribute<V> attr = (Attribute<V>) this.attributes.remove(key);
        return attr == null ? null : attr.get();
    }

    @SuppressWarnings("unchecked")
    public <V> void set(String key, V value) {
        Attribute<V> attr = (Attribute<V>) this.attributes.computeIfAbsent(key, k -> new Attribute<V>());
        attr.set(value);
    }

    @SuppressWarnings("unchecked")
    public <V> V get(String key) {
        Attribute<V> attr = (Attribute<V>) this.attributes.get(key);
        return attr == null ? null : attr.get();
    }

}
