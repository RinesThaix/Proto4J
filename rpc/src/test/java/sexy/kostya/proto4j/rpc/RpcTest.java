package sexy.kostya.proto4j.rpc;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.junit.Assert;
import org.junit.Test;
import sexy.kostya.proto4j.exception.RpcException;
import sexy.kostya.proto4j.rpc.transport.RpcServer;
import sexy.kostya.proto4j.rpc.transport.conclave.RpcConclaveServer;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

/**
 * Created by k.shandurenko on 02.10.2020
 */
public class RpcTest {

    private final static String LOCALHOST = "127.0.0.1";
    private final static int    PORT      = 6775;

    @Test
    public void testBase() throws ExecutionException, InterruptedException {
        RpcServer server = new RpcServer(2, 2);
        server.start(PORT).toCompletableFuture().get();

        RpcClientPerformer performer = new RpcClientPerformer(2, 2);
        performer.connect(LOCALHOST, PORT).toCompletableFuture().get();

        RpcClientUser user = new RpcClientUser(2, 2);
        user.connect(LOCALHOST, PORT).toCompletableFuture().get();

        TestService svc = user.getService();

        Assert.assertSame(0, svc.get());

        svc.set(2, 3);
        Thread.sleep(10);
        Assert.assertSame(5, svc.get());

        svc.setWithFuture(17, 10).toCompletableFuture().get();
        Assert.assertSame(27, svc.get());

        Assert.assertSame(6, svc.sum(1, 2, 3).toCompletableFuture().get());

        Assert.assertSame(28, svc.sumArray(new int[]{1, 2, 3, 4, 5, 6, 7}));

        Assert.assertSame(36, svc.sumList(Lists.newArrayList(1, 2, 3, 4, 5, 6, 7, 8)));

        Assert.assertArrayEquals(null, svc.plusOne(new long[]{4, -1, 1, 3, 6, 9}));

        RpcClientPerformer performer2 = new RpcClientPerformer(2, 2);
        performer2.connect(LOCALHOST, PORT).toCompletableFuture().get();

        svc.broadcastTest();
        Thread.sleep(10);
        Assert.assertSame(-1, svc.get());

        svc.broadcastTest(true);
        Assert.assertSame(-2, svc.get());

        performer.shutdown();

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

        Assert.assertEquals(300, svc.sumOfAges2(Sets.newHashSet(
                Sets.newHashSet(
                        new AutoTestData("John", "12345", 20),
                        new AutoTestData("Albert", "1029384756", 35),
                        new AutoTestData("Gilbert", "09871234650", 45)),
                Sets.newHashSet(
                        new AutoTestData("Adam", "55555", 20),
                        new AutoTestData("Emma", "artificial99", 36),
                        new AutoTestData("Octavius", "heart&me", 45)),
                Sets.newHashSet(
                        new AutoTestData("Boris", "99percentVodka", 20),
                        new AutoTestData("Donald", "I_AM_TRUMP", 35),
                        new AutoTestData("Tim", "8burton8", 44))
        )));

        svc.print(new AutoTestDataExtended("Konstantin", "123123123", 23, UUID.randomUUID()));

        try {
            svc.testException().toCompletableFuture().get();
            Assert.fail();
        } catch (ExecutionException ex) {
            Assert.assertEquals(RpcException.Code.EXECUTION_EXCEPTION, ((RpcException) ex.getCause()).getCode());
        } catch (Throwable t) {
            throw t;
        }

        performer2.shutdown();

        try {
            svc.get();
            Assert.fail();
        } catch (RpcException ex) {
            Assert.assertEquals(RpcException.Code.NO_SERVICE_AVAILABLE, ex.getCode());
        } catch (Throwable t) {
            throw t;
        }

        user.shutdown();
        server.shutdown();

        Assert.assertFalse(user.getChannel().isActive());
    }

    @Test
    public void testConclaveServers() throws Throwable {
        List<InetSocketAddress> serversAddresses = Lists.newArrayList(
                new InetSocketAddress(LOCALHOST, PORT),
                new InetSocketAddress(LOCALHOST, PORT + 1)
        );

        RpcConclaveServer srv1 = new RpcConclaveServer(serversAddresses, 2, 2);
        srv1.start(serversAddresses.get(0)).toCompletableFuture().get();

        RpcConclaveServer srv2 = new RpcConclaveServer(serversAddresses, 2, 2);
        srv2.start(serversAddresses.get(1)).toCompletableFuture().get();

        RpcClientPerformer performer1 = new RpcClientPerformer(2, 2);
        performer1.connect(serversAddresses.get(1)).toCompletableFuture().get();

        RpcClientUser user = new RpcClientUser(2, 2);
        user.connect(serversAddresses.get(0)).toCompletableFuture().get();

        TestService svc = user.getService();

        Assert.assertSame(0, svc.get());
        svc.setWithFuture(5, 6).toCompletableFuture().get();
        Assert.assertSame(11, svc.get());

        RpcClientPerformer performer2 = new RpcClientPerformer(2, 2);
        performer2.connect(serversAddresses.get(0)).toCompletableFuture().get();

        svc.print(new AutoTestDataExtended("Alice", "321321321", 24, UUID.randomUUID()));

        try {
            svc.testException().toCompletableFuture().get();
            Assert.fail();
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (!(cause instanceof RpcException) || ((RpcException) cause).getCode() != RpcException.Code.EXECUTION_EXCEPTION) {
                throw ex;
            }
        } catch (Throwable t) {
            throw t;
        }

        user.shutdown();
        performer1.shutdown();
        performer2.shutdown();
        srv2.shutdown();
        srv1.shutdown();
    }

    @Test
    public void testConclaveFull() throws ExecutionException, InterruptedException {
        List<InetSocketAddress> serversAddresses = Lists.newArrayList(
                new InetSocketAddress(LOCALHOST, PORT),
                new InetSocketAddress(LOCALHOST, PORT + 1)
        );

        RpcConclaveServer srv1 = new RpcConclaveServer(serversAddresses, 2, 2);
        srv1.start(serversAddresses.get(0)).toCompletableFuture().get();

        RpcConclaveServer srv2 = new RpcConclaveServer(serversAddresses, 2, 2);
        srv2.start(serversAddresses.get(1)).toCompletableFuture().get();

        RpcConclaveClientPerformer performer1 = new RpcConclaveClientPerformer(serversAddresses, 2, 2);
        performer1.connect().toCompletableFuture().get();

        RpcConclaveClientUser user1 = new RpcConclaveClientUser(serversAddresses, 2, 2);
        user1.connect().toCompletableFuture().get();

        RpcConclaveClientUser user2 = new RpcConclaveClientUser(serversAddresses, 2, 2);
        user2.connect().toCompletableFuture().get();

        TestService svc1 = user1.getService();
        TestService svc2 = user2.getService();

        svc1.setWithFuture(10, 20).toCompletableFuture().get();
        Assert.assertEquals(30, svc2.get());

        Assert.assertEquals(15, svc1.sumArray(new int[]{3, 7, 5}));
        Assert.assertEquals(20, svc2.sumList(Lists.newArrayList(-100, 20, 10, 5, 15, 70)));

        srv1.shutdown();
        user1.shutdown();
        user2.shutdown();
        performer1.shutdown();
        srv2.shutdown();
    }

}
