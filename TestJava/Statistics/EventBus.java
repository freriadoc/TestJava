package Statistics;

import java.util.function.Consumer;
import java.util.function.Predicate;

public interface EventBus {

    <T> void addSubscriber(Class<T> clazz, Consumer<T> subscriber);

    <T> void addSubscriberForFilteredEvents(Class<T> clazz, Predicate<T> filter, Consumer<T> subscriber);

    <T> void publishEvent(T event);
}