package sexy.kostya.proto4j.rpc;

import com.google.common.collect.Lists;
import sexy.kostya.proto4j.rpc.transport.RpcServer;

import java.util.concurrent.ExecutionException;

/**
 * Created by k.shandurenko on 02.10.2020
 */
public class RpcTest {

    public static void main(String[] args) throws ExecutionException, InterruptedException {
        RpcServer server = new RpcServer(2, 2);
        server.start(6775).toCompletableFuture().get();

        RpcClientPerformer performer = new RpcClientPerformer(2, 2);
        performer.connect("127.0.0.1", 6775).toCompletableFuture().get();

        RpcClientUser user = new RpcClientUser(2, 2);
        user.connect("127.0.0.1", 6775).toCompletableFuture().get();

        TestService svc = user.getService();
        System.out.println("GET: " + svc.get());
        svc.set(2, 3);
        Thread.sleep(100);
        System.out.println("GET: " + svc.get());
        svc.setWithFuture(17, 10).toCompletableFuture().get();
        System.out.println("GET: " + svc.get());
        System.out.println("SUM: " + svc.sum(1, 2, 3).toCompletableFuture().get());
        System.out.println("SUM ARRAY: " + svc.sumArray(new int[]{1, 2, 3, 4, 5, 6, 7}));
        System.out.println("SUM LIST: " + svc.sumList(Lists.newArrayList(1, 2, 3, 4, 5, 6, 7, 8)));

        user.stop();
        performer.stop();
        server.stop();
    }

}
