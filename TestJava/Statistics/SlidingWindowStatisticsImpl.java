package Statistics;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class SlidingWindowStatisticsImpl implements SlidingWindowStatistics {
    private final EventBus eventBus; // Use the EventBus interface
    private final LockFreeRingBuffer<Measurement> measurements; // Use LockFreeRingBuffer<Measurement>
    private final ThrottlerImpl throttler;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean hasNewMeasurements = new AtomicBoolean(false); // Flag to track new measurements

    public SlidingWindowStatisticsImpl(int maxMeasurementsPerSecond) {
        this.eventBus = new EventBusImpl(); // Initialize with EventBusImpl
        this.measurements = new LockFreeRingBuffer<>(1000); // Set a capacity for the ring buffer
        this.throttler = new ThrottlerImpl(maxMeasurementsPerSecond,1000);
        this.scheduler = Executors.newScheduledThreadPool(1);

        // Schedule the task to run every 10 milliseconds
        scheduler.scheduleAtFixedRate(this::publishStatistics, 0, 10, TimeUnit.MILLISECONDS);
    }

    @Override
    public void add(int measurement) {
        if (throttler.shouldProceed() == ThrottleResult.PROCEED) {
            long currentTime = System.currentTimeMillis();

            // Add the new measurement with the current timestamp
            if (measurements.add(new Measurement(measurement, currentTime))) {
                hasNewMeasurements.set(true);
            }
        }
    }

    private void publishStatistics() {
        if (hasNewMeasurements.get()) { // Only publish if there are new measurements
            try {
                // Publish the updated statistics
                eventBus.publishEvent(getLatestStatistics());
            } catch (Exception e) {
                System.err.println("Error publishing statistics: " + e.getMessage());
            } finally {
                hasNewMeasurements.set(false);
            }
        }
    }
    private void cleanupOldMeasurements(long currentTime) {
        // Use the iterator to go through the measurements
        for (Measurement measurement : measurements) {
            if (measurement != null && currentTime - measurement.timestamp > 1000) {
                // If the measurement is older than 1 second, advance the tail
                measurements.advanceTail();
            } else {
                break; // Stop if we find a valid measurement
            }
        }
    }

    private @NotNull HashMap<Integer, Integer> getCurrentHistogram() {
        HashMap<Integer, Integer> histogram = new HashMap<>();

        // Use the iterator to go through the measurements
        for (Measurement measurement : measurements) {
            if (measurement != null) { // Check for null to avoid NullPointerException
                // Update the histogram with the current measurement
                histogram.merge(measurement.value, 1, Integer::sum);
            }
        }
        return histogram;
    }

    @Override
    public void subscribeForStatistics(Predicate<Statistics> filter, Consumer<Statistics> subscriber) {
        // Create a Predicate<BaseEvent> that casts the event to Statistics
        Predicate<BaseEvent> eventFilter = event -> event instanceof Statistics && filter.test((Statistics) event);

        // Create a Consumer<BaseEvent> that calls the subscriber if the event is a Statistics
        Consumer<BaseEvent> eventSubscriber = event -> {
            if (event instanceof Statistics) {
                subscriber.accept((Statistics) event);
            }
        };

        // Add the subscriber for filtered events
        eventBus.addSubscriberForFilteredEvents(StatisticsImpl.class, eventFilter, eventSubscriber);
    }

    @Override
    public Statistics getLatestStatistics() {
        long currentTime = System.currentTimeMillis();
        cleanupOldMeasurements(currentTime);
        HashMap<Integer, Integer> histogram = getCurrentHistogram();
        return new StatisticsImpl(histogram);
    }

    private record Measurement(int value, long timestamp) {
    }

    public void shutdown() {
        scheduler.shutdown(); // Stop accepting new tasks
        try {
            // Wait for existing tasks to terminate
            if (!scheduler.awaitTermination(60, TimeUnit.SECONDS)) {
                scheduler.shutdownNow(); // Force shutdown if tasks did not terminate
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow(); // Force shutdown if interrupted
            Thread.currentThread().interrupt(); // Restore interrupted status
        }
    }

    public record StatisticsImpl(
            HashMap<Integer, Integer> histogram) implements Statistics, BaseEvent { // Extend BaseEvent

        @Override
            public double getMean() {
                double sum = 0;
                int count = 0;
                for (Map.Entry<Integer, Integer> entry : histogram.entrySet()) {
                    sum += entry.getKey() * entry.getValue();
                    count += entry.getValue();
                }
                return count == 0 ? 0 : sum / count;
            }

        @Override
        public int getMode() {
            int mode = 0;
            int maxCount = 0;

            for (Map.Entry<Integer, Integer> entry : histogram.entrySet()) {
                int count = entry.getValue();
                if (count > maxCount) {
                    maxCount = count;
                    mode = entry.getKey();
                }
            }
            return mode;
        }

            @Override
            public double getPctile(int pctile) {
                int[] measurements = histogram.keySet().stream().mapToInt(Integer::intValue).toArray();
                int index = (int) Math.ceil(pctile / 100.0 * measurements.length);
                return measurements.length > 0 ? measurements[Math.min(index - 1, measurements.length - 1)] : 0;
            }
        }
}