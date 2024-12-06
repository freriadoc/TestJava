package Statistics;

import java.util.function.Consumer;
import java.util.function.Predicate;

public interface SlidingWindowStatistics {
    void add(int measurement);

     void subscribeForStatistics(Predicate<Statistics> filter, Consumer<Statistics> subscriber);

    Statistics getLatestStatistics();

    interface Statistics extends BaseEvent{
        double getMean();
        int getMode();
        double getPctile(int pctile);
    }
}