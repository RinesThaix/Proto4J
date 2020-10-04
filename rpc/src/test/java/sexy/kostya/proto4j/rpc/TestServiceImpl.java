package sexy.kostya.proto4j.rpc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sexy.kostya.proto4j.serialization.annotation.Nullable;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by k.shandurenko on 02.10.2020
 */
public class TestServiceImpl implements TestService {

    private final Logger        logger = LoggerFactory.getLogger(TestService.class);
    private       AtomicInteger value  = new AtomicInteger();

    @Override
    public void set(int a, int b) {
        this.value.set(a + b);
    }

    @Override
    public CompletionStage<Void> setWithFuture(int a, int b) {
        set(a, b);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public int get() {
        return this.value.get();
    }

    @Override
    public CompletionStage<Integer> sum(int a, int b, int c) {
        return CompletableFuture.completedFuture(a + b + c);
    }

    @Override
    public int sumArray(int[] array) {
        int result = 0;
        for (int el : array) {
            result += el;
        }
        return result;
    }

    @Override
    public int sumList(List<Integer> array) {
        int result = 0;
        for (int el : array) {
            result += el;
        }
        return result;
    }

    @Override
    public long[] plusOne(long[] in) {
        for (int i = 0; i < in.length; ++i) {
            in[i]++;
        }
        return null;
    }

    @Override
    public void broadcastTest() {
        this.value.set(-1);
    }

    @Override
    public CompletionStage<Void> broadcastTest(boolean val) {
        this.value.set(-2);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public int sumOfAges(Set<Set<@Nullable TestData>> datas) {
        int sum = 0;
        for (Set<TestData> set : datas) {
            for (TestData data : set) {
                if (data != null) {
                    sum += data.getAge();
                }
            }
        }
        return sum;
    }

    @Override
    public int sumOfAges2(Set<Set<AutoTestData>> datas) {
        AutoTestData data = datas.iterator().next().iterator().next();
        this.logger.info("Printing from sumOfAges2: {}", data);
        return datas.stream().mapToInt(ds -> ds.stream().mapToInt(AutoTestData::getAge).sum()).sum();
    }

    @Override
    public void print(AutoTestDataExtended data) {
        this.logger.info("Printing {}", data);
    }

    @Override
    public CompletionStage<Void> testException() {
        throw new RuntimeException("Exception");
    }
}
