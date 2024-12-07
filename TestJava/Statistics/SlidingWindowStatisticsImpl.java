package Statistics;

import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
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

            List<Measurement> snapshot;
            synchronized (measurements) {
                // Add the new measurement with the current timestamp
                measurements.add(new Measurement(measurement, currentTime));

                // Clean old measurements and get a snapshot of measures.
                snapshot = getSnapshot();
            }
            ConcurrentHashMap<Integer, Integer> histogram = getCurrentHistogramFromSnapshot(snapshot);

            // Publish the updated statistics
            eventBus.publishEvent(new StatisticsImpl(histogram)); // Ensure StatisticsImpl extends BaseEvent
        }
    }

    private List<Measurement> getSnapshot() {
        long currentTime = System.currentTimeMillis();

        Iterator<Measurement> iterator = measurements.iterator();

        // Clean up old measurements from the original deque
        while (iterator.hasNext()) {
            Measurement measurement = iterator.next();
            if (currentTime - measurement.timestamp >= 1000) {
                iterator.remove(); // Remove the old measurement
            } else {
                break; // Stop iterating when we find a measurement that is not too old
            }
        }
        // Create a snapshot of the current measurements after cleanup
        return new ArrayList<>(measurements);
    }

    private @NotNull ConcurrentHashMap<Integer, Integer> getCurrentHistogramFromSnapshot(@NotNull List<Measurement> snapshot) {
        ConcurrentHashMap<Integer, Integer> histogram = new ConcurrentHashMap<>();
        for (Measurement measurement : snapshot) {
            // Update the histogram with the current measurement
            histogram.merge(measurement.value, 1, Integer::sum);
        }
        return histogram;
    }

    private @NotNull ConcurrentHashMap<Integer, Integer> getCurrentHistogram() {
        List<Measurement> snapshot = getSnapshot();
        return getCurrentHistogramFromSnapshot(snapshot);
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

    private record Measurement(int value, long timestamp) {

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