package Statistics;

public class Event<T> {
    private final T event;

    public Event(T event) {
        this.event = event;
    }

    public T getEvent() {
        return event;
    }
}