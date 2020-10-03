package sexy.kostya.proto4j.rpc.transport.conclave;

import com.google.common.base.Preconditions;
import sexy.kostya.proto4j.commons.Proto4jException;
import sexy.kostya.proto4j.commons.Proto4jProperties;
import sexy.kostya.proto4j.rpc.transport.RpcClient;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by k.shandurenko on 03.10.2020
 */
public class RpcConclaveClient extends RpcClient {

    private final List<InetSocketAddress> allServersAddresses;

    public RpcConclaveClient(List<InetSocketAddress> allServersAddresses, int workerThreads, int handlerThreads) {
        super(workerThreads, handlerThreads);
        Preconditions.checkArgument(!allServersAddresses.isEmpty(), "There must be at least one server address");
        this.allServersAddresses = allServersAddresses;
    }

    public CompletionStage<Void> connect() {
        AtomicInteger           index   = new AtomicInteger();
        long                    timeout = Proto4jProperties.getProperty("conclaveTimeout", 1000L);
        CompletableFuture<Void> result  = new CompletableFuture<>();
        Thread thread = new Thread(() -> {
            while (true) {
                try {
                    int id = index.getAndIncrement();
                    if (id == this.allServersAddresses.size()) {
                        result.completeExceptionally(new Proto4jException("No servers available"));
                        break;
                    }
                    InetSocketAddress addr = this.allServersAddresses.get(id);
                    connect(addr.getHostName(), addr.getPort()).toCompletableFuture().get(timeout, TimeUnit.MILLISECONDS);
                    result.complete(null);
                    break;
                } catch (ExecutionException | TimeoutException ex) {
                    shutdown();
                } catch (Throwable t) {
                    shutdown();
                    result.completeExceptionally(t);
                    break;
                }
            }
        }, "Proto4j RpcConclaveClient Checking Thread");
        thread.start();
        return result;
    }

    @Override
    public final CompletionStage<Void> start(String address, int port) {
        return super.start(address, port);
    }

}
