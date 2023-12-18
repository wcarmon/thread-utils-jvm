package io.github.wcarmon.thread;

import com.google.common.collect.EvictingQueue;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A global, Threadsafe, error sink
 *
 * <p>Attempts to limit memory usage and to never hide errors
 */
public final class ErrSink {

    private static final int CAPACITY = 128;

    private final List<Runnable> onReachedCapacityListeners;

    /** Ring buffer */
    private final EvictingQueue<Throwable> q;

    private final ReentrantReadWriteLock stateLock;

    public ErrSink() {
        this.onReachedCapacityListeners = new ArrayList<>(32);
        this.q = EvictingQueue.create(CAPACITY);
        this.stateLock = new ReentrantReadWriteLock();
    }

    /**
     * Register listener, invoked when the queue is full
     *
     * @param listener invoked when the queue is full
     */
    public void addOnReachedCapacityListener(Runnable listener) {
        Objects.requireNonNull(listener, "listener is required and null.");

        stateLock.writeLock().lock();
        try {
            onReachedCapacityListeners.add(listener);

        } finally {
            stateLock.writeLock().unlock();
        }
    }

    /**
     * @return all stored exceptions
     */
    public List<Throwable> exceptions() {
        this.stateLock.readLock().lock();
        try {
            return List.copyOf(q);

        } finally {
            this.stateLock.readLock().unlock();
        }
    }

    /**
     * @return true when the queue is not empty
     */
    public boolean hasExceptions() {
        this.stateLock.readLock().lock();
        try {
            return !q.isEmpty();

        } finally {
            this.stateLock.readLock().unlock();
        }
    }

    /**
     * Persists an exception to the queue
     *
     * @param t to persist
     */
    public void report(Throwable t) {
        if (t == null) {
            // -- noop
            return;
        }

        boolean mustFireListeners;
        this.stateLock.writeLock().lock();
        try {
            q.add(t);
            mustFireListeners = q.size() >= CAPACITY;

        } finally {
            this.stateLock.writeLock().unlock();
        }

        if (!mustFireListeners) {
            return;
        }

        for (var listener : onReachedCapacityListeners) {
            try {
                listener.run();
            } catch (Exception ex) {
                System.err.println("Failed to report exception: " + ex);
            }
        }
    }

    /**
     * @return count of stored exceptions, limited to queue capacity
     */
    public int size() {
        this.stateLock.readLock().lock();
        try {
            return q.size();

        } finally {
            this.stateLock.readLock().unlock();
        }
    }
}
