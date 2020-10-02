package sexy.kostya.proto4j.rpc;

import com.google.common.util.concurrent.ListenableFuture;
import sexy.kostya.proto4j.rpc.annotation.Broadcast;
import sexy.kostya.proto4j.rpc.annotation.Index;
import sexy.kostya.proto4j.rpc.annotation.Proto4jService;

import java.util.List;
import java.util.Set;

/**
 * Created by k.shandurenko on 30.09.2020
 */
@Proto4jService
public interface TestService {

    @Broadcast
    void printSum(List<List<Integer>> list);

    int sum(Set<Integer> set);

    ListenableFuture<Integer> sumAsync(Set<Integer> set);

    void printNums(@Index int a, @Index int b);

}
