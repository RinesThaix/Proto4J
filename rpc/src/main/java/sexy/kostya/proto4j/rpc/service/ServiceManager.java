package sexy.kostya.proto4j.rpc.service;

/**
 * Created by k.shandurenko on 02.10.2020
 */
public interface ServiceManager {

    <S> S getService(Class<S> serviceClass);

    <S, I extends S> int registerService(Class<S> serviceClass, I implementation);

}
