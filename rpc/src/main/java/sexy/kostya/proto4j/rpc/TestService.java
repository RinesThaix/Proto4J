package sexy.kostya.proto4j.rpc;

import sexy.kostya.proto4j.rpc.annotation.Broadcast;
import sexy.kostya.proto4j.rpc.annotation.Index;
import sexy.kostya.proto4j.rpc.annotation.Proto4jService;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionStage;

/**
 * Created by k.shandurenko on 30.09.2020
 */
@Proto4jService
public interface TestService {

    @Broadcast
    void printSum(List<List<Integer>> list);

    int sum(Set<Integer> set);

    CompletionStage<Integer> sumAsync(Set<Integer> set);

    void printNums(@Index int a, @Index int b);

}
