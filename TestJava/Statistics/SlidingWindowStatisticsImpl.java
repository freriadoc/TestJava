package Statistics;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class SlidingWindowStatisticsImpl implements SlidingWindowStatistics {
    private final EventBusImpl eventBus;
    private final ConcurrentHashMap<Integer, Integer> histogram;

    public SlidingWindowStatisticsImpl() {
        this.eventBus = new EventBusImpl();
        this.histogram = new ConcurrentHashMap<>();
    }

    @Override
    public void add(int measurement) {
        histogram.merge(measurement, 1, (oldValue, newValue) -> oldValue + newValue);
        eventBus.publishEvent(new StatisticsImpl(histogram));
    }

    @Override
    public void subscribeForStatistics(Predicate<Statistics> filter, Consumer<Statistics> subscriber) {
        eventBus.addSubscriberForFilteredEvents(Statistics.class, filter, subscriber);
    }

    @Override
    public StatisticsImpl getLatestStatistics() {
        return new StatisticsImpl(histogram);
    }

    private class StatisticsImpl implements SlidingWindowStatistics.Statistics {
        private final ConcurrentHashMap<Integer, Integer> histogram;

        public StatisticsImpl(ConcurrentHashMap<Integer, Integer> histogram) {
            this.histogram = histogram;
        }

        @Override
        public double getMean() {
            double sum = 0;
            for (Integer measurement : histogram.keySet()) {
                sum += measurement * histogram.get(measurement);
            }
            return sum / histogram.values().stream().mapToInt(Integer::intValue).sum();
        }

        @Override
        public int getMode() {
            return histogram.entrySet().stream()
                    .max((entry1, entry2) -> entry1.getValue().compareTo(entry2.getValue()))
                    .map(Map.Entry::getKey)
                    .orElse(0);
        }

        @Override
        public double getPctile(int pctile) {
            int[] measurements = histogram.keySet().stream().mapToInt(Integer::intValue).toArray();
            int index = (int) Math.ceil(pctile / 100.0 * measurements.length);
            return measurements[index - 1];
        }
    }
}