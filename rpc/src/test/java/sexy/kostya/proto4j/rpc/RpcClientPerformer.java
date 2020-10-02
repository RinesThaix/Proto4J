package sexy.kostya.proto4j.rpc;

import sexy.kostya.proto4j.rpc.transport.RpcClient;

import java.util.concurrent.CompletionStage;

/**
 * Created by k.shandurenko on 02.10.2020
 */
public class RpcClientPerformer extends RpcClient {

    public RpcClientPerformer(int workerThreads, int handlerThreads) {
        super(workerThreads, handlerThreads);
    }

    @Override
    public CompletionStage<Void> start(String address, int port) {
        return super.start(address, port).thenAccept(v -> {
            getServiceManager().registerService(TestService.class, new TestServiceImpl());
        });
    }
}
