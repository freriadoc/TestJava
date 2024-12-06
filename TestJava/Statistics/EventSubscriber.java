package Statistics;

import java.util.function.Consumer;
import java.util.function.Predicate;

public class EventSubscriber<BaseEvent> {
    final Predicate<BaseEvent> filter;
    final Consumer<BaseEvent> subscriber;

    public EventSubscriber(Consumer<BaseEvent> subscriber) {
        this.subscriber = subscriber;
        this.filter = null; // No filter
    }

    public EventSubscriber(Predicate<BaseEvent> filter, Consumer<BaseEvent> subscriber) {
        this.filter = filter;
        this.subscriber = subscriber;
    }

    public boolean test(BaseEvent event) {
        return filter == null || filter.test(event);
    }
}