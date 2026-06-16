package com.osfans.trime.core;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;


/**
 * Implementation of RimeLifecycle, handling state transitions and observer notification.
 * This class replaces the use of Kotlin's MutableStateFlow.
 */
public class RimeLifecycleImpl implements RimeLifecycle {

    // Thread-safe holder for the current state.
    private final AtomicReference<State> currentState = new AtomicReference<>(State.STOPPED);

    // Thread-safe set of registered observers.
    private final Set<StateObserver> observers = Collections.synchronizedSet(new HashSet<>());

    // Dedicated single-threaded executor for lifecycle-bound tasks (replaces CoroutineScope).
    private final ExecutorService lifecycleExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r);
        t.setName("rime-lifecycle");
        return t;
    });

    @Override
    public State getCurrentState() {
        return currentState.get();
    }

    @Override
    public void addObserver(StateObserver observer) {
        observers.add(observer);
        // Immediately notify of the current state upon subscription, mimicking StateFlow behavior.
        observer.onStateChange(currentState.get());
    }

    @Override
    public void removeObserver(StateObserver observer) {
        observers.remove(observer);
    }

    @Override
    public Executor getLifecycleExecutor() {
        return lifecycleExecutor;
    }

    /**
     * Executes a state transition, validates the transition, and notifies observers.
     * @param newState The target state.
     */
    public void emitState(State newState) {
        State oldState = currentState.get();

        // 1. Check for valid transition (replaces Kotlin's checkAtState logic)
        checkTransition(oldState, newState);

        // 2. Perform state update
        if (currentState.compareAndSet(oldState, newState)) {
            //Timber.d("RimeLifecycle transition: %s -> %s", oldState, newState);

            // 3. Handle cleanup on STOPPED
            if (newState == State.STOPPED) {
                // Replicates CoroutineScope.cancelChildren() logic by cancelling all tasks.
                lifecycleExecutor.shutdownNow();
            }

            // 4. Notify all observers
            for (StateObserver observer : observers) {
                try {
                    observer.onStateChange(newState);
                } catch (Exception e) {
                    //Timber.e(e, "Observer failed to handle state change to %s", newState);
                }
            }
        }
    }

    private void checkTransition(State oldState, State newState) {
        State expectedOldState = null;
        switch (newState) {
            case STARTING:
                expectedOldState = State.STOPPED;
                break;
            case READY:
                expectedOldState = State.STARTING;
                break;
            case STOPPING:
                expectedOldState = State.READY;
                break;
            case STOPPED:
                expectedOldState = State.STOPPING;
                break;
        }

        if (expectedOldState != null && oldState != expectedOldState) {
            throw new IllegalStateException("Invalid RimeLifecycle transition. Expected " + expectedOldState + " but found " + oldState + " when trying to move to " + newState);
        }
    }
}
