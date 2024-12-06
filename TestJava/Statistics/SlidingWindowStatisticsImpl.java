package Statistics;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.ArrayList;

public class SlidingWindowStatisticsImpl implements SlidingWindowStatistics {
    private final EventBus eventBus; // Use the EventBus interface
    private final ConcurrentLinkedDeque<Measurement> measurements;
    private final ThrottlerImpl throttler;

    public SlidingWindowStatisticsImpl(int maxMeasurementsPerSecond) {
        this.eventBus = new EventBusImpl(); // Initialize with EventBusImpl
        this.measurements = new ConcurrentLinkedDeque<>();
        this.throttler = new ThrottlerImpl(maxMeasurementsPerSecond);
    }

    @Override
    public void add(int measurement) {
        if (throttler.shouldProceed() == ThrottleResult.PROCEED) {
            long currentTime = System.currentTimeMillis();
            synchronized (measurements) {
                // Add the new measurement with the current timestamp
                measurements.add(new Measurement(measurement, currentTime));
                // Remove measurements older than 1 second
                cleanOldMeasurements(currentTime);
            }
            // Publish the updated statistics
            eventBus.publishEvent(new StatisticsImpl(getCurrentHistogram())); // Ensure StatisticsImpl extends BaseEvent
        }
    }

    private void cleanOldMeasurements(long currentTime) {
        Measurement oldestMeasurement;
        while ((oldestMeasurement = measurements.peekFirst()) != null && (currentTime - oldestMeasurement.timestamp >= 1000)) {
            measurements.pollFirst();
        }
    }

    private ConcurrentHashMap<Integer, Integer> getCurrentHistogram() {
        // Create a snapshot of the current measurements
        List<Measurement> snapshot;
        synchronized (measurements) {
            snapshot = new ArrayList<>(measurements);
        }

        ConcurrentHashMap<Integer, Integer> histogram = new ConcurrentHashMap<>();
        for (Measurement measurement : snapshot) {
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
        return new StatisticsImpl(getCurrentHistogram());
    }

    private static class Measurement {
        final int value;
        final long timestamp;

        Measurement(int value, long timestamp) {
            this.value = value;
            this.timestamp = timestamp;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Measurement that = (Measurement) o;
            return value == that.value && timestamp == that.timestamp;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(value, timestamp);
        }
    }

    public static class StatisticsImpl implements Statistics, BaseEvent { // Extend BaseEvent
        private final ConcurrentHashMap<Integer, Integer> histogram;

        public StatisticsImpl(ConcurrentHashMap<Integer, Integer> histogram) {
            this.histogram = histogram;
        }

        @Override
        public double getMean() {
            double sum = 0;
            int count = 0;
            for (Integer measurement : histogram.keySet()) {
                sum += measurement * histogram.get(measurement);
                count += histogram.get(measurement);
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
            return measurements .length > 0 ? measurements[Math.min(index - 1, measurements.length - 1)] : 0;
        }
    }
}