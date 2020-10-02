package sexy.kostya.proto4j.rpc;

import sexy.kostya.proto4j.rpc.annotation.Broadcast;
import sexy.kostya.proto4j.rpc.annotation.Proto4jService;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * Created by k.shandurenko on 02.10.2020
 */
@Proto4jService
public interface TestService {

    void set(int a, int b);

    CompletionStage<Void> setWithFuture(int a, int b);

    int get();

    CompletionStage<Integer> sum(int a, int b, int c);

    int sumArray(int[] array);

    int sumList(List<Integer> array);

    long[] plusOne(long[] in);

    @Broadcast
    void broadcastTest();

    @Broadcast
    CompletionStage<Void> broadcastTest(boolean val);

}
