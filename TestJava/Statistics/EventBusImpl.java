package Statistics;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Predicate;


public class EventBusImpl implements EventBus {

    private final ConcurrentHashMap<Class<?>, List<EventSubscriber<?>>> subscribers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Class<?>, Object> latestEvents = new ConcurrentHashMap<>();
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    @Override
    public <T> void addSubscriber(Class<T> clazz, Consumer<T> subscriber) {
        subscribers.computeIfAbsent(clazz, key -> new ArrayList<>()).add(new EventSubscriber<>(subscriber));
    }

    @Override
    public <T> void addSubscriberForFilteredEvents(Class<T> clazz, Predicate<T> filter, Consumer<T> subscriber) {
        Objects.requireNonNull(subscriber, "subscriber cannot be null");
        subscribers.computeIfAbsent(clazz, key -> new ArrayList<>()).add(new EventSubscriber<>(filter, subscriber));
    }

    @Override
    public <T> void publishEvent(T event) {
        latestEvents.put(event.getClass(), event);
        List<EventSubscriber<?>> consumers = subscribers.get(event.getClass());
        if (consumers != null) {
            consumers.forEach(consumer -> executorService.submit(() -> ((Consumer<T>)consumer).accept((T)latestEvents.get(event.getClass()))));
        }
    }

    private class EventSubscriber<T> implements Consumer<T> {
        private final Predicate<T> filter;
        private final Consumer<T> subscriber;
    
        public EventSubscriber(Predicate<T> filter, Consumer<T> subscriber) {
            this.filter = filter;
            this.subscriber = subscriber;
        }
        public EventSubscriber(Consumer<T> subscriber) {
            this.filter = null;
            this.subscriber = subscriber;
        }
    
        @Override
        public void accept(T event) {
            if (filter != null && filter.test(event)) {
                subscriber.accept(event);
            }
        }
    }
}