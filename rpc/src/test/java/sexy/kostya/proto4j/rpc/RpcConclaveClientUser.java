package sexy.kostya.proto4j.rpc;

import sexy.kostya.proto4j.rpc.transport.conclave.RpcConclaveClient;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * Created by k.shandurenko on 03.10.2020
 */
public class RpcConclaveClientUser extends RpcConclaveClient {

    private final TestService service;

    public RpcConclaveClientUser(List<InetSocketAddress> allServersAddresses, int workerThreads, int handlerThreads) {
        super(allServersAddresses, workerThreads, handlerThreads);
        this.service = getServiceManager().getService(TestService.class);
    }

    public TestService getService() {
        return service;
    }

}
