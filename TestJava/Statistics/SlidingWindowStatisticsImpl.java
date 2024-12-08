package Statistics;

import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class SlidingWindowStatisticsImpl implements SlidingWindowStatistics {
    private final EventBus eventBus; // Use the EventBus interface
    private final Deque<Measurement> measurements; // Use a Deque<Measurement>
    private final ThrottlerImpl throttler;

    public SlidingWindowStatisticsImpl(int maxMeasurementsPerSecond) {
        this.eventBus = new EventBusImpl(); // Initialize with EventBusImpl
        this.measurements = new ArrayDeque<>(); // Use an ArrayDeque
        this.throttler = new ThrottlerImpl(maxMeasurementsPerSecond);
    }

    @Override
    public void add(int measurement) {
        if (throttler.shouldProceed() == ThrottleResult.PROCEED) {
            long currentTime = System.currentTimeMillis();

            HashMap<Integer, Integer> histogram;
            synchronized (measurements) {
                // Add the new measurement with the current timestamp
                measurements.add(new Measurement(measurement, currentTime));
                cleanupOldMeasurements(currentTime);
                histogram = getCurrentHistogram();
            }

            // Publish the updated statistics
            eventBus.publishEvent(new StatisticsImpl(histogram)); // Ensure StatisticsImpl extends BaseEvent
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
        HashMap<Integer, Integer> histogram;
        synchronized (measurements) {
            histogram = getCurrentHistogram();
        }
        return new StatisticsImpl(histogram);
    }

    private record Measurement(int value, long timestamp) {
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
            int index = (int) Math.ceil(pctile / 100.0 * measurements.length);
            return measurements.length > 0 ? measurements[Math.min(index - 1, measurements.length - 1)] : 0;
        }
    }
}