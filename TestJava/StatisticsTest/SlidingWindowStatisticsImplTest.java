package StatisticsTest;

import Statistics.SlidingWindowStatistics;
import Statistics.SlidingWindowStatisticsImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class SlidingWindowStatisticsImplTest {
    private SlidingWindowStatisticsImpl statistics;

    @BeforeEach
    public void setUp() {
        statistics = new SlidingWindowStatisticsImpl(5); // Allow 5 measurements per second
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
                stats -> true, // No filter, subscribe to all
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
}