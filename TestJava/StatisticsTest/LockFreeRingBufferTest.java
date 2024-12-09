package StatisticsTest;

import Statistics.LockFreeRingBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class LockFreeRingBufferTest {
    private LockFreeRingBuffer<Integer> ringBuffer;

    @BeforeEach
    public void setUp() {
        ringBuffer = new LockFreeRingBuffer<>(5); // Create a ring buffer with a capacity of 5
    }

    @Test
    public void testAddAndSize() {
        assertTrue(ringBuffer.add(1));
        assertTrue(ringBuffer.add(2));
        assertTrue(ringBuffer.add(3));
        assertEquals(3, ringBuffer.size());
        assertTrue(ringBuffer.add(4));
        assertTrue(ringBuffer.add(5));
        assertFalse(ringBuffer.add(6)); // Buffer is full
        assertEquals(5, ringBuffer.size());
    }

    @Test
    public void testGet() {
        ringBuffer.add(1);
        ringBuffer.add(2);
        ringBuffer.add(3);
        assertEquals(1, ringBuffer.get(0));
        assertEquals(2, ringBuffer.get(1));
        assertEquals(3, ringBuffer.get(2));
    }

    @Test
    public void testAdvanceTail() {
        ringBuffer.add(1);
        ringBuffer.add(2);
        ringBuffer.add(3);
        assertEquals(3, ringBuffer.size());
        ringBuffer.advanceTail(); // Remove the oldest element
        assertEquals(2, ringBuffer.size());
        assertEquals(2, ringBuffer.get(0)); // The new head should be 2
        assertEquals(3, ringBuffer.get(1)); // The next element should still be 3
    }

    @Test
    public void testIterator() {
        ringBuffer.add(1);
        ringBuffer.add(2);
        ringBuffer.add(3);

        int count = 0;
        for (Integer value : ringBuffer) {
            assertNotNull(value);
            count++;
        }
        assertEquals(3, count);
    }

    @Test
    public void testIteratorEmpty() {
        int count = 0;
        for (Integer _ : ringBuffer) {
            count++;
        }
        assertEquals(0, count); // Should be empty
    }

    @Test
    public void testWrapAround() {
        for (int i = 1; i <= 5; i++) {
            assertTrue(ringBuffer.add(i));
        }
        assertFalse(ringBuffer.add(6)); // Buffer is full

        ringBuffer.advanceTail(); // Remove the oldest element
        assertTrue(ringBuffer.add(6)); // Should succeed now

        int count = 0;
        for (Integer _ : ringBuffer) {
            count++;
        }
        assertEquals(5, count); // Should still have 5 elements
    }

    @Test
    public void testSizeAfterAdvance() {
        ringBuffer.add(1);
        ringBuffer.add(2);
        ringBuffer.advanceTail(); // Remove the oldest element
        assertEquals(1, ringBuffer.size()); // Size should be 1
    }
}