package StatisticsTest;

import Statistics.ThrottleResult;
import Statistics.ThrottlerImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class ThrottlerImplTest {
    private ThrottlerImpl throttler;

    @BeforeEach
    public void setUp() {
        throttler = new ThrottlerImpl(5,1000); // Allow 5 operations per second
    }

    @Test
    public void testShouldProceedUnderLimit() {
        for (int i = 0; i < 5; i++) {
            assertEquals(ThrottleResult.PROCEED, throttler.shouldProceed());
        }
    }

    @Test
    public void testShouldNotProceedOverLimit() {
        for (int i = 0; i < 5; i++) {
            throttler.shouldProceed(); // Fill the limit
        }

        // The next call should not proceed
        assertEquals(ThrottleResult.DO_NOT_PROCEED, throttler.shouldProceed());
    }

    @Test
    public void testThrottlingResetsAfterOneSecond() throws InterruptedException {
        for (int i = 0; i < 5; i++) {
            throttler.shouldProceed(); // Fill the limit
        }

        // The next call should not proceed
        assertEquals(ThrottleResult.DO_NOT_PROCEED, throttler.shouldProceed());

        // Wait for 1 second to allow the throttler to reset
        Thread.sleep(1000);

        // Now it should allow operations again
        assertEquals(ThrottleResult.PROCEED, throttler.shouldProceed());
    }

    @Test
    public void testNotifyWhenCanProceed() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        throttler.notifyWhenCanProceed(result -> {
            assertEquals(ThrottleResult.PROCEED, result);
            latch.countDown();
        });

        // Fill the limit
        for (int i = 0; i < 5; i++) {
            throttler.shouldProceed();
        }

        // Wait for the notification
        assertFalse(latch.await(1, TimeUnit.SECONDS)); // Should not be notified yet

        // Wait for 1 second to allow the throttler to reset
        Thread.sleep(1000);

        // Now it should allow operations again, which should trigger the notification
        assertTrue(latch.await(1, TimeUnit.SECONDS)); // Should be notified now
    }
}