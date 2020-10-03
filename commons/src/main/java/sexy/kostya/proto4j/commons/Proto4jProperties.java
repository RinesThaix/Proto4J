package sexy.kostya.proto4j.commons;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by k.shandurenko on 03.10.2020
 */
public class Proto4jProperties {

    private final static String              PREFIX            = "proto4j.";
    private final static Map<String, Object> CACHED_PROPERTIES = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public static <T> T getProperty(String name, T defaultValue) {
        return (T) CACHED_PROPERTIES.computeIfAbsent(name, n -> {
            String property = System.getProperty(PREFIX + n);
            if (property == null) {
                return defaultValue;
            }
            Class<?> clazz = defaultValue.getClass();
            if (clazz == boolean.class || clazz == Boolean.class) {
                return Boolean.parseBoolean(property);
            }
            if (clazz == int.class || clazz == Integer.class) {
                return Integer.parseInt(property);
            }
            if (clazz == long.class || clazz == Long.class) {
                return Long.parseLong(property);
            }
            if (clazz == String.class) {
                return property;
            }
            throw new Proto4jException("Unknown proto4j property class " + clazz.getSimpleName() + " for \"" + name + "\"");
        });
    }

}
