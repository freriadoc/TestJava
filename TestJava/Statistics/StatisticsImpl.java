package Statistics;

import java.util.HashMap;
import java.util.Map;

public record StatisticsImpl(
        HashMap<Integer, Integer> histogram) implements SlidingWindowStatistics.Statistics, BaseEvent { // Extend BaseEvent
    @Override
    public boolean isCoalescing(){ return true;}
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