package Statistics;

import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class EventBusImpl implements EventBus {

    private final ConcurrentHashMap<Class<? extends BaseEvent>, Queue<EventSubscriber<BaseEvent>>> subscribers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Class<? extends BaseEvent>, BaseEvent> latestEvents = new ConcurrentHashMap<>(); // Store latest events
    private final BlockingQueue<Event<BaseEvent>> eventQueue = new LinkedBlockingQueue<>(); // Event queue
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    public EventBusImpl() {
        // Start the event processing thread
        Thread eventProcessorThread = new Thread(this::processEvents);
        eventProcessorThread.start();
    }

    @Override
    public void addSubscriber(Class<? extends BaseEvent> clazz, Consumer<BaseEvent> subscriber){
        Objects.requireNonNull(subscriber, "subscriber cannot be null");
        subscribers.computeIfAbsent(clazz, key -> new ConcurrentLinkedQueue<>()).add(new EventSubscriber<BaseEvent>(subscriber));
    }


    @Override
    public void addSubscriberForFilteredEvents(Class<? extends BaseEvent> clazz, Predicate<BaseEvent> filter, Consumer<BaseEvent> subscriber) {
        Objects.requireNonNull(subscriber, "subscriber cannot be null");

        // Create a new EventSubscriber with the correct types
        EventSubscriber<BaseEvent> eventSubscriber = new EventSubscriber<BaseEvent>(filter, subscriber);

        subscribers.computeIfAbsent(clazz, key -> new ConcurrentLinkedQueue<>()).add(eventSubscriber);
    }

    @Override
    public boolean publishEvent(BaseEvent event) {
        if ( event.isCoalescing())
        {
            Queue<EventSubscriber<BaseEvent>> consumers = subscribers.get(event.getClass());
            if (consumers != null) {
                // Check if the event is different from the last published event
                if (isDifferentEvent(event, latestEvents.get(event.getClass()))) {
                    latestEvents.put(event.getClass(), event); // Update the latest event
                    // Add the event to the queue for processing
                    return eventQueue.offer(new Event<>(event));
                }
            }
            return true;
        }
        else {
            // Add the event to the queue for processing
            return eventQueue.offer(new Event<>(event));
        }
    }

    private boolean isDifferentEvent(BaseEvent newEvent, BaseEvent lastEvent) {
        if (lastEvent == null) {
            return true; // If no last event, consider it different
        }
        return !newEvent.equals(lastEvent); // Compare events
    }

    private void processEvents() {
        while (true) {
            try {
                Event<BaseEvent> event = eventQueue.take(); // Block until an event is available
                BaseEvent baseEvent = event.getEvent(); // Get the event
                Class<? extends BaseEvent> eventClass = baseEvent.getClass();
                Queue<EventSubscriber<BaseEvent>> consumers = subscribers.get(eventClass);

                if (consumers != null) {
                    // Create a list of CompletableFutures for each consumer
                    CompletableFuture[] futures = consumers.stream()
                            .map(consumer -> CompletableFuture.runAsync(() -> {
                                try {
                                    // Check if the event passes the filter (if any)
                                    if (consumer.test(baseEvent)) {
                                        consumer.subscriber.accept(baseEvent);
                                    }
                                } catch (Exception e) {
                                    System.err.println("Error processing event: " + e.getMessage());
                                }
                            }, executorService)) // Use the executor service for async execution
                            .toArray(CompletableFuture[]::new);

                    // Combine all futures and wait for them to complete
                    CompletableFuture<Void> allOf = CompletableFuture.allOf(futures);
                    allOf.join(); // Wait for all consumers to finish processing
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Restore interrupted status
                break; // Exit the loop if interrupted
            }
        }
    }


    @Override
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
                if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                    System.err.println("Executor service did not terminate");
                }
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt(); // Restore interrupted status
        }
    }
}
