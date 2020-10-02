package sexy.kostya.proto4j.rpc;

import com.google.common.collect.Lists;
import org.junit.Assert;
import org.junit.Test;
import sexy.kostya.proto4j.rpc.transport.RpcServer;

import java.util.concurrent.ExecutionException;

/**
 * Created by k.shandurenko on 02.10.2020
 */
public class RpcTest {

    @Test
    public void testRpc() throws ExecutionException, InterruptedException {
        RpcServer server = new RpcServer(2, 2);
        server.start(6775).toCompletableFuture().get();

        RpcClientPerformer performer = new RpcClientPerformer(2, 2);
        performer.connect("127.0.0.1", 6775).toCompletableFuture().get();

        RpcClientUser user = new RpcClientUser(2, 2);
        user.connect("127.0.0.1", 6775).toCompletableFuture().get();

        TestService svc = user.getService();

        Assert.assertSame(0, svc.get());

        svc.set(2, 3);
        Thread.sleep(100);
        Assert.assertSame(5, svc.get());

        svc.setWithFuture(17, 10).toCompletableFuture().get();
        Assert.assertSame(27, svc.get());

        Assert.assertSame(6, svc.sum(1, 2, 3).toCompletableFuture().get());

        Assert.assertSame(28, svc.sumArray(new int[]{1, 2, 3, 4, 5, 6, 7}));

        Assert.assertSame(36, svc.sumList(Lists.newArrayList(1, 2, 3, 4, 5, 6, 7, 8)));

        Assert.assertArrayEquals(new long[]{5, 0, 2, 4, 7, 10}, svc.plusOne(new long[]{4, -1, 1, 3, 6, 9}));

        RpcClientPerformer performer2 = new RpcClientPerformer(2, 2);
        performer2.connect("127.0.0.1", 6775).toCompletableFuture().get();

        svc.broadcastTest();
        Thread.sleep(100);
        Assert.assertSame(-1, svc.get());

        svc.broadcastTest(true);
        Assert.assertSame(-2, svc.get());

        performer.stop();

        Assert.assertSame(2, svc.sumArray(new int[]{3, -1}));

        performer2.stop();

        try {
            svc.get();
            Assert.fail();
        } catch (AssertionError ex) {
            throw ex;
        } catch (Exception ex) {
            Assert.assertEquals("Could not find implementation for service", ex.getCause().getMessage());
        }

        server.stop();
        Thread.sleep(100);

        Assert.assertFalse(user.getChannel().isActive());
    }

}
