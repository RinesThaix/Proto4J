package sexy.kostya.proto4j.rpc;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.junit.Assert;
import org.junit.Test;
import sexy.kostya.proto4j.exception.RpcException;
import sexy.kostya.proto4j.rpc.transport.RpcServer;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

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

        Assert.assertEquals(300, svc.sumOfAges(Sets.newHashSet(
                Sets.newHashSet(
                        new TestData("John", "12345", 20),
                        new TestData("Albert", "1029384756", 35),
                        new TestData("Gilbert", "09871234650", 45)),
                Sets.newHashSet(
                        new TestData("Adam", "55555", 20),
                        new TestData("Emma", "artificial99", 36),
                        new TestData("Octavius", "heart&me", 45)),
                Sets.newHashSet(
                        new TestData("Boris", "99percentVodka", 20),
                        new TestData("Donald", "I_AM_TRUMP", 35),
                        new TestData("Tim", "8burton8", 44))
        )));

        try {
            svc.testException().toCompletableFuture().get();
            Assert.fail();
        } catch (ExecutionException ex) {
            Assert.assertEquals(RpcException.Code.INVOCATION_EXCEPTION, ((RpcException) ex.getCause()).getCode());
        } catch (Throwable t) {
            throw t;
        }

        performer2.stop();

        try {
            svc.get();
            Assert.fail();
        } catch (RpcException ex) {
            Assert.assertEquals(RpcException.Code.NO_SERVICE_AVAILABLE, ex.getCode());
        } catch (Throwable t) {
            throw t;
        }

        server.stop();
        Thread.sleep(100);

        Assert.assertFalse(user.getChannel().isActive());
    }

}
