package com.osfans.trime.core;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * RimeDispatcher is a wrapper of a single-threaded executor that runs RimeController.
 * It provides a method for dispatching jobs to the executor's internal queue.
 * It also provides a stop() method to gracefully stop the executor and return the remaining jobs.
 *
 * Adapted from [fcitx5-android/FcitxDispatcher.kt].
 */
public final class RimeDispatcher {

    // --- Static Constants (from Companion Object) ---

    private static final long JOB_WAITING_LIMIT = 2000L; // ms

    // 使用固定大小的线程池
    private final ExecutorService executor = Executors.newFixedThreadPool(1);

    public <T> T submit(Callable<T> block) {
        try {
            //return block.call();
            return executor.submit(block).get(2,TimeUnit.SECONDS);
        } catch (Exception e) {
            e.printStackTrace();
           return null;
        }
    }

    // --- Interfaces ---

    public interface RimeController {
        void nativeStartup();
        void nativeFinalize();
    }

    // --- WrappedRunnable Class ---

    /**
     * Wraps a standard Runnable to track execution time and provide a debug name.
     */
    public static final class WrappedRunnable implements Runnable {
        private final Runnable runnable;
        private final String name;
        private final long time;
        private boolean started = false;

        public WrappedRunnable(Runnable runnable, String name) {
            this.runnable = runnable;
            this.name = name;
            this.time = System.currentTimeMillis();
        }

        public WrappedRunnable(Runnable runnable) {
            this(runnable, null);
        }

        public boolean isStarted() {
            return started;
        }

        private long getDelta() {
            return System.currentTimeMillis() - time;
        }

        @Override
        public void run() {
            long delta = getDelta();
            if (delta > JOB_WAITING_LIMIT) {
                //Timber.w("%s has waited %d ms to get run since created!", toString(), delta);
            }
            started = true;
            runnable.run();
        }

        @Override
        public String toString() {
            return "WrappedRunnable[" + (name != null ? name : String.valueOf(hashCode())) + "]";
        }

        public static final WrappedRunnable EMPTY = new WrappedRunnable(() -> {}, "Empty");

        // Simple Getter for the underlying Runnable (useful for 'stop()' return value)
        public Runnable getUnderlyingRunnable() {
            return runnable;
        }
    }

    // --- RimeDispatcher Fields ---

    private final RimeController controller;
    private final ExecutorService internalExecutor;
    private final LinkedBlockingQueue<WrappedRunnable> queue = new LinkedBlockingQueue<>();
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final Object lifecycleLock = new Object(); // Replaces Kotlin's Mutex

    // --- Constructor ---

    public RimeDispatcher(RimeController controller) {
        this.controller = controller;
        // Simulates the Executors.newSingleThreadExecutor { Thread(it, "rime-main") } setup
        this.internalExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setName("rime-main");
            return t;
        });
    }

    // --- Public Methods ---

    /**
     * Start the dispatcher.
     * This function immediately executes the native startup process on the single thread
     * and begins processing the job queue.
     */
    public void start() {

        // Launch the main loop on the single thread
        internalExecutor.execute(() -> {
            synchronized (lifecycleLock) {
                if (isRunning.compareAndSet(false, true)) {
                    try {
                        controller.nativeStartup();

                        // Main message loop: runs until 'isRunning' is set to false
                        while (isRunning.get() && !Thread.currentThread().isInterrupted()) {
                            // Blocks until a job is available (similar to Kotlin's queue.take())
                            WrappedRunnable block = queue.take();

                            // The 'EMPTY' sentinel is used to break the loop on stop()
                            if (block == WrappedRunnable.EMPTY) {
                                break;
                            }
                            block.run();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt(); // Restore interrupt status
                    } finally {
                        controller.nativeFinalize();
                        // Executor is not shut down here, just the loop breaks.
                    }
                }
            }
        });
    }

    /**
     * Stop the dispatcher gracefully.
     * This function blocks until the dispatcher's main loop and native finalize are complete.
     *
     * @return A list of the underlying Runnables that were not executed (remaining jobs).
     */
    public List<Runnable> stop() {
        if (isRunning.compareAndSet(true, false)) {
            // 1. Offer the sentinel to break the blocking 'queue.take()' in the main loop
            queue.offer(WrappedRunnable.EMPTY);

            // 2. Block until the main loop finishes its synchronized section (nativeFinalize done)
            //    We submit a blocking task and wait for its completion.
            Future<List<Runnable>> future = internalExecutor.submit((Callable<List<Runnable>>) () -> {
                // This code runs *after* the main loop finishes its lifecycleLock section.
                synchronized (lifecycleLock) {
                    List<WrappedRunnable> rest = new ArrayList<>();
                    // 3. Drain any remaining jobs from the queue (including the sentinel if not consumed)
                    queue.drainTo(rest);

                    // Convert WrappedRunnable list to Runnable list for return
                    List<Runnable> result = new ArrayList<>(rest.size());
                    for (WrappedRunnable wrapped : rest) {
                        if (wrapped != WrappedRunnable.EMPTY) {
                            result.add(wrapped.getUnderlyingRunnable());
                        }
                    }
                    return result;
                }
            });

            // Block and wait for the final task to complete (i.e., the loop has stopped and queue is drained)
            try {
                // Shut down the executor after the main loop has finished its work
                internalExecutor.shutdown();
                // We use future.get() to block until the shutdown-cleanup task is done.
                return future.get();
            } catch (InterruptedException | ExecutionException e) {
                // Attempt a forced shutdown if waiting failed
                internalExecutor.shutdownNow();
                // Return what we can, likely an empty list due to the exception
                return new ArrayList<>();
            }
        } else {
            return new ArrayList<>();
        }
    }

    /**
     * Dispatches a Runnable block to the Rime processing queue.
     *
     * @param block The Runnable job to execute.
     */
    // Replaces the CoroutineDispatcher's dispatch method
    public void dispatch(Runnable block) {
        if (!isRunning.get()) {
            throw new IllegalStateException("Dispatcher is not in running state!");
        }
        queue.offer(new WrappedRunnable(block));
    }
}
