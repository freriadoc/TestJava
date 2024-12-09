package Statistics;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class ThrottlerImpl implements Throttler {
    private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
    private final int maxOperationsPerSecond;
    private final long slidingWindowDuration; // Duration in milliseconds
    private final Queue<Long> operationTimestamps;

    public ThrottlerImpl(int maxOperationsPerSecond, long slidingWindowDuration) {
        this.maxOperationsPerSecond = maxOperationsPerSecond;
        this.slidingWindowDuration = slidingWindowDuration;
        this.operationTimestamps = new ConcurrentLinkedQueue<>();
    }

    @Override
    public ThrottleResult shouldProceed() {
        long currentTime = System.currentTimeMillis();

        // Remove timestamps older than the sliding window duration
        while (!operationTimestamps.isEmpty() && (currentTime - operationTimestamps.peek() >= slidingWindowDuration)) {
            operationTimestamps.poll();
        }

        if (operationTimestamps.size() < maxOperationsPerSecond) {
            operationTimestamps.add(currentTime);
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