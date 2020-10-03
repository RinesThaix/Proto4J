package sexy.kostya.proto4j.rpc;

import sexy.kostya.proto4j.rpc.transport.conclave.RpcConclaveClient;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * Created by k.shandurenko on 02.10.2020
 */
public class RpcConclaveClientPerformer extends RpcConclaveClient {

    public RpcConclaveClientPerformer(List<InetSocketAddress> allServersAddresses, int workerThreads, int handlerThreads) {
        super(allServersAddresses, workerThreads, handlerThreads);
    }

    @Override
    public CompletionStage<Void> connect() {
        return super.connect()
                .thenCompose(v -> getServiceManager().registerService(TestService.class, new TestServiceImpl()))
                .thenApply(sid -> null);
    }
}
