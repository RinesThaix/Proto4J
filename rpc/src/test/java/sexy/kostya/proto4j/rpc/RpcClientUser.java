package sexy.kostya.proto4j.rpc;

import sexy.kostya.proto4j.rpc.transport.RpcClient;

/**
 * Created by k.shandurenko on 02.10.2020
 */
public class RpcClientUser extends RpcClient {

    private final TestService service;

    public RpcClientUser(int workerThreads, int handlerThreads) {
        super(workerThreads, handlerThreads);
        this.service = getServiceManager().getService(TestService.class);
    }

    public TestService getService() {
        return service;
    }
}
