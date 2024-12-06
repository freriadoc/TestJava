package Statistics;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class ThrottlerImpl implements Throttler {
    private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
    private final int maxOperationsPerSecond;
    private final Queue<Long> operationTimestamps;
    private final ReentrantLock lock = new ReentrantLock(); // Lock for thread safety

    public ThrottlerImpl(int maxOperationsPerSecond) {
        this.maxOperationsPerSecond = maxOperationsPerSecond;
        this.operationTimestamps = new LinkedList<>();
    }

    @Override
    public ThrottleResult shouldProceed() {
        long currentTime = System.currentTimeMillis();
        lock.lock(); // Acquire the lock
        try {
            // Remove timestamps older than 1 second
            while (!operationTimestamps.isEmpty() && (currentTime - operationTimestamps.peek() >= 1000)) {
                operationTimestamps.poll();
            }

            if (operationTimestamps.size() < maxOperationsPerSecond) {
                operationTimestamps.add(currentTime);
                return ThrottleResult.PROCEED;
            } else {
                return ThrottleResult.DO_NOT_PROCEED;
            }
        } finally {
            lock.unlock(); // Ensure the lock is released
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