package Statistics;

import java.util.function.Consumer;
import java.util.function.Predicate;

public interface EventBus {

    /**
     * Adds a subscriber for events of the specified type.
     *
     * @param clazz the class of the event type
     * @param subscriber the consumer that will handle the event
     */
    void addSubscriber(Class<? extends BaseEvent> clazz, Consumer<BaseEvent> subscriber);

    /**
     * Adds a subscriber for events of the specified type with a filter.
     *
     * @param clazz the class of the event type
     * @param filter the predicate to filter events
     * @param subscriber the consumer that will handle the event
     */
    void addSubscriberForFilteredEvents(Class<? extends BaseEvent> clazz, Predicate<BaseEvent> filter, Consumer<BaseEvent> subscriber);

    /**
     * Publishes an event to all subscribers.
     *
     * @param event the event to publish
     */
    boolean publishEvent(BaseEvent event);

    /**
     * Publishes a coalesced event to all subscribers, ensuring that only unique events are processed.
     *
     * @param event the event to publish
     */
    boolean publishCoalescedEvent(BaseEvent event);


    /**
     * Shuts down the event bus, stopping all event processing.
     */
    void shutdown();
}