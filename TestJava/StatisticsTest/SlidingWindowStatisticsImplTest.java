package StatisticsTest;

import Statistics.EventBus;
import Statistics.EventBusImpl;
import Statistics.SlidingWindowStatistics;
import Statistics.SlidingWindowStatisticsImpl;
import Statistics.Throttler;
import Statistics.ThrottlerImpl; // Assuming you have a ThrottlerImpl class

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class SlidingWindowStatisticsImplTest {
    private SlidingWindowStatisticsImpl statistics;
    private final int maxMeasurementsPerSecond = 100;
    private final int numberOfThreads = 10;
    private final int measurementsPerThread = 100;
    private final int ringBufferCapacity = 1000; // Define a capacity for the ring buffer

    @BeforeEach
    public void setUp() {
        EventBus eventBus = new EventBusImpl(); // Create an instance of EventBus
        Throttler throttler = new ThrottlerImpl(maxMeasurementsPerSecond, 1000); // Create an instance of Throttler
        statistics = new SlidingWindowStatisticsImpl(eventBus, throttler, ringBufferCapacity); // Inject dependencies
    }

    @AfterEach
    public void tearDown() {
        statistics.shutdown();
    }

    @Test
    public void testAddMeasurements() {
        for (int i = 1; i <= 5; i++) {
            statistics.add(i);
        }

        // Check the latest statistics
        SlidingWindowStatistics.Statistics stats = statistics.getLatestStatistics();
        assertEquals(3.0, stats.getMean(), 0.01);
        assertEquals(1, stats.getMode());
        assertEquals(5, stats.getPctile(100));
    }

    @Test
    public void testThrottling() {
        for (int i = 1; i <= 10; i++) {
            statistics.add(i);
        }

        // Only the first 5 measurements should be processed
        SlidingWindowStatistics.Statistics stats = statistics.getLatestStatistics();
        assertEquals(3.0, stats.getMean(), 0.01);
        assertEquals(1, stats.getMode());
        assertEquals(5, stats.getPctile(100));
    }

    @Test
    public void testSlidingWindowBehavior() throws InterruptedException {
        for (int i = 1; i <= 5; i++) {
            statistics.add(i);
        }

        // Wait for 1 second to allow the window to slide
        Thread.sleep(1000);

        // Add new measurements after the first window has slid
        for (int i = 6; i <= 10; i++) {
            statistics.add(i);
        }

        // Check the latest statistics
        SlidingWindowStatistics.Statistics stats = statistics.getLatestStatistics();
        assertEquals(8.0, stats.getMean(), 0.01);
        assertEquals(6, stats.getMode());
        assertEquals(10, stats.getPctile(100));
    }

    @Test
    public void testSubscribeForStatistics() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        statistics.subscribeForStatistics(
                _ -> true, // No filter, subscribe to all
                stats -> {
                    assertEquals(3.0, stats.getMean(), 0.01);
                    latch.countDown();
                }
        );

        for (int i = 1; i <= 5; i++) {
            statistics.add(i);
        }

        // Wait for the subscriber to be notified
        assertTrue(latch.await(1, TimeUnit.SECONDS));
    }

    @Test
    public void testMeanWithNoMeasurements() {
        SlidingWindowStatistics.Statistics stats = statistics.getLatestStatistics();
        assertEquals(0.0, stats.getMean(), 0.01);
    }

    @Test
    public void testModeWithNoMeasurements() {
        SlidingWindowStatistics.Statistics stats = statistics.getLatestStatistics();
        assertEquals(0, stats.getMode());
    }

    @Test
    public void testPctileWithNoMeasurements() {
        SlidingWindowStatistics.Statistics stats = statistics.getLatestStatistics();
        assertEquals(0.0, stats.getPctile(50), 0.01);
    }

    @Test
    public void testThrottlingUnderRapidAdditions() throws InterruptedException {
        for (int i = 1; i <= 10; i++) {
            statistics.add(i);
            Thread.sleep(100); // Add a slight delay to simulate rapid additions
        }

        // Only the first 5 measurements should be processed
        SlidingWindowStatistics.Statistics stats = statistics.getLatestStatistics();
        assertEquals(3.0, stats.getMean(), 0.01);
        assertEquals(1, stats.getMode());
        assertEquals(5, stats.getPctile(100));
    }

    @Test
    public void testConcurrentAdditions() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(numberOfThreads);
        try (ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads)) {

            // Create and start threads to add measurements
            for (int i = 0; i < numberOfThreads; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    for (int j = 0; j < measurementsPerThread; j++) {
                        statistics.add(threadId); // Each thread adds its thread ID as the measurement
                    }
                    latch.countDown(); // Signal that this thread is done
                });
            }

            latch.await(); // Wait for all threads to finish
            executor.shutdown();
            executor.awaitTermination(1, TimeUnit.MINUTES); // Wait for the executor to finish
        }

        // Verify the statistics
        SlidingWindowStatistics.Statistics latestStatistics = statistics.getLatestStatistics();
        HashMap<Integer, Integer> histogram = latestStatistics.histogram();

        // Check that the histogram contains the expected counts
        for (int i = 0; i < numberOfThreads; i++) {
            assertEquals(measurementsPerThread, histogram.getOrDefault(i, 0), "Count for measurement " + i + " should be " + measurementsPerThread);
        }
    }
}