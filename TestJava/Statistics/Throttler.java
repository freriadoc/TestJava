package Statistics;

import java.util.function.Consumer;

public interface Throttler {
    /**
     * Checks if the operation should proceed based on the throttling rules.
     *
     * @return ThrottleResult indicating whether the operation can proceed or not.
     */
    ThrottleResult shouldProceed();

    /**
     * Registers a consumer that will be notified when the operation can proceed.
     *
     * @param consumer The consumer to be notified when the operation can proceed.
     */
    void notifyWhenCanProceed(Consumer<ThrottleResult> consumer);
}