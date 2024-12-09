package StatisticsTest;

import Statistics.BaseEvent;
import Statistics.EventBusImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

public class EventBusImplTest {

    private EventBusImpl eventBus;

    @BeforeEach
    public void setUp() {
        eventBus = new EventBusImpl();
    }

    @AfterEach
    public void tearDown() {
        eventBus.shutdown();
    }

    @Test
    public void testAddSubscriberAndPublishEvent() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        String[] receivedMessage = new String[1];

        // Create a subscriber that sets the received message
        Consumer<BaseEvent> subscriber = event -> {
            receivedMessage[0] = ((TestEvent) event).message();
            latch.countDown();
        };

        eventBus.addSubscriber(TestEvent.class, subscriber);

        // Publish an event
        TestEvent event = new TestEvent("Hello, World!");
        eventBus.publishEvent(event);

        // Wait for the event to be processed
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals("Hello, World!", receivedMessage[0]);
    }

    @Test
    public void testPublishCoalescedEvent() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        String[] receivedMessage = new String[1];

        // Create a subscriber that sets the received message
        Consumer<BaseEvent> subscriber = event -> {
            receivedMessage[0] = ((TestEvent) event).message();
            latch.countDown();
        };

        eventBus.addSubscriber(TestEvent.class, subscriber);

        // Publish an event
        TestEvent event1 = new TestEvent("Hello, World!");
        eventBus.publishEvent(event1);

        // Publish the same event again (should be coalesced)
        eventBus.publishEvent(event1);

        // Wait for the event to be processed
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals("Hello, World!", receivedMessage[0]);
    }

    @Test
    public void testFilteredEventSubscription() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        String[] receivedMessage = new String[1];

        // Create a subscriber with a filter
        Consumer<BaseEvent> subscriber = event -> {
            receivedMessage[0] = ((TestEvent) event).message();
            latch.countDown();
        };

        eventBus.addSubscriberForFilteredEvents(TestEvent.class, event -> ((TestEvent) event).message().contains("Hello"), subscriber);

        // Publish an event that matches the filter
        TestEvent event = new TestEvent("Hello, World!");
        eventBus.publishEvent(event);

        // Wait for the event to be processed
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals("Hello, World!", receivedMessage[0]);

        // Publish an event that does not match the filter
        TestEvent event2 = new TestEvent("Goodbye, World!");
        eventBus.publishEvent(event2);

        // Ensure the latch has not been counted down
        assertFalse(latch.await(1, TimeUnit.MILLISECONDS));
    }

    // Test event class for testing purposes
        private record TestEvent(String message) implements BaseEvent {
            public boolean isCoalescing() {
                return true;
            }

        @Override
            public boolean equals(Object obj) {
                if (this == obj) return true;
                if (!(obj instanceof TestEvent other)) return false;
            return message.equals(other.message);
            }

    }
}