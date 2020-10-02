package sexy.kostya.proto4j.rpc.serialization.annotation;

/**
 * Created by k.shandurenko on 03.10.2020
 */

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Transient {
}
