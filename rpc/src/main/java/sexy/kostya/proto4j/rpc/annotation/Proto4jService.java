package sexy.kostya.proto4j.rpc.annotation;

/**
 * Created by k.shandurenko on 30.09.2020
 */
public @interface Proto4jService {

    int explicitIdentifier() default 0;
}
