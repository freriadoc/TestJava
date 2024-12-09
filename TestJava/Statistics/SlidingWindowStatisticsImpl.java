package Statistics;

import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class SlidingWindowStatisticsImpl implements SlidingWindowStatistics {
    private final EventBus eventBus; // Use the EventBus interface
    private final ConcurrentLinkedDeque<Measurement> measurements; // Use a Deque<Measurement>
    private final ThrottlerImpl throttler;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean hasNewMeasurements = new AtomicBoolean(false);// Flag to track new measurements

    public SlidingWindowStatisticsImpl(int maxMeasurementsPerSecond) {
        this.eventBus = new EventBusImpl(); // Initialize with EventBusImpl
        this.measurements = new ConcurrentLinkedDeque<>(); // Use an ArrayDeque
        this.throttler = new ThrottlerImpl(maxMeasurementsPerSecond);
        this.scheduler = Executors.newScheduledThreadPool(1);

        // Schedule the task to run every 10 milliseconds
        scheduler.scheduleAtFixedRate(this::publishStatistics, 0, 10, TimeUnit.MILLISECONDS);
    }

    @Override
    public void add(int measurement) {
        if (throttler.shouldProceed() == ThrottleResult.PROCEED) {
            long currentTime = System.currentTimeMillis();

            // Add the new measurement with the current timestamp
            measurements.add(new Measurement(measurement, currentTime));
            hasNewMeasurements.set(true);
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
        Iterator<Measurement> iterator = measurements.iterator();
        while (iterator.hasNext()) {
            Measurement measurement = iterator.next();
            if (currentTime - measurement.timestamp > 1000) {
                iterator.remove(); // Remove the measurement if it's older than one second
            } else {
                // Since the measurements are sorted by timestamp, we can break early
                break;
            }
        }
    }
    private @NotNull HashMap<Integer, Integer> getCurrentHistogram() {
        HashMap<Integer, Integer> histogram = new HashMap<>();
        for (Measurement measurement : measurements) {
            // Update the histogram with the current measurement
            histogram.merge(measurement.value, 1, Integer::sum);
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

    public static class StatisticsImpl implements Statistics, BaseEvent { // Extend BaseEvent
        private final HashMap<Integer, Integer> histogram;

        public StatisticsImpl(HashMap<Integer, Integer> histogram) {
            this.histogram = histogram;
        }

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
            return histogram.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(0);
        }

        @Override
        public double getPctile(int pctile) {
            int[] measurements = histogram.keySet().stream().mapToInt(Integer::intValue).toArray();
            int index = (int) Math .ceil(pctile / 100.0 * measurements.length);
            return measurements.length > 0 ? measurements[Math.min(index - 1, measurements.length - 1)] : 0;
        }
    }
}