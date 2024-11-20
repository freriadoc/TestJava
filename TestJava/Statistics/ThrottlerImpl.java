package Statistics;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class ThrottlerImpl implements Throttler {
    private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
    private final int maxOperationsPerSecond;
    private int operationsCount;
    private long lastOperationTime;

    public ThrottlerImpl(int maxOperationsPerSecond) {
        this.maxOperationsPerSecond = maxOperationsPerSecond;
        this.operationsCount = 0;
        this.lastOperationTime = System.currentTimeMillis();
    }

    @Override
    public ThrottleResult shouldProceed() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastOperationTime >= 1000) {
            operationsCount = 0;
            lastOperationTime = currentTime;
        }
        if (operationsCount < maxOperationsPerSecond) {
            operationsCount++;
            return ThrottleResult.PROCEED;
        } else {
            return ThrottleResult.DO_NOT_PROCEED;
        }
    }

    @Override
    public void notifyWhenCanProceed(Consumer<ThrottleResult> consumer) {
        executorService.scheduleAtFixedRate(() -> {
            ThrottleResult result = shouldProceed();
            if (result == ThrottleResult.PROCEED) {
                consumer.accept(result);
            }
        }, 0, 10, TimeUnit.MILLISECONDS);
    }
}
