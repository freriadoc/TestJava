package Statistics;

import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;

public class LockFreeRingBuffer<T> implements Iterable<T> {
    private final AtomicReferenceArray<T> buffer;
    private final AtomicInteger head;
    private final AtomicInteger tail;
    private final int capacity;

    public LockFreeRingBuffer(int capacity) {
        this.capacity = capacity;
        this.buffer = new AtomicReferenceArray<>(capacity);
        this.head = new AtomicInteger(0);
        this.tail = new AtomicInteger(0);
    }

    public boolean add(T item) {
        int currentHead = head.get();
        int nextHead = (currentHead + 1) % capacity;

        // Check if the buffer is full
        if (nextHead == tail.get()) {
            return false; // Buffer is full
        }

        // Attempt to set the item at the current head position
        if (buffer.compareAndSet(currentHead, null, item)) {
            head.set(nextHead); // Move head forward
            return true;
        }
        return false; // Failed to add item
    }

    public T get(int index) {
        return buffer.get(index);
    }

    public void advanceTail() {
        tail.set((tail.get() + 1) % capacity);
    }

    public int size() {
        return (head.get() - tail.get() + capacity) % capacity;
    }

    @Override
    public @NotNull Iterator<T> iterator() {
        return new RingBufferIterator();
    }

    private class RingBufferIterator implements Iterator<T> {
        private int currentIndex = tail.get();
        private final int endIndex = head.get();

        @Override
        public boolean hasNext() {
            return currentIndex != endIndex;
        }

        @Override
        public T next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            T item = buffer.get(currentIndex);
            currentIndex = (currentIndex + 1) % capacity;
            return item;
        }
    }
}