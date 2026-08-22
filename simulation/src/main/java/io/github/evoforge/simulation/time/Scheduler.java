package io.github.evoforge.simulation.time;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;

/** Deterministic scheduler whose retained storage follows pending work rather than processed history. */
public final class Scheduler {

    private static final Comparator<ScheduledTask> ORDER = Comparator.comparingLong(ScheduledTask::when)
            .thenComparing(ScheduledTask::scheduleOrder);

    private final HandlerRegistry handlers;
    private final NavigableSet<ScheduledTask> queue = new TreeSet<>(ORDER);
    private final Map<TaskHandle, ScheduledTask> activeTasks = new HashMap<>();
    private final List<ScheduledTask> dueBatch = new ArrayList<>();

    /** Reusable handle slots. Slots are reused with a new generation. */
    private final ArrayDeque<Long> freeHandleSlots = new ArrayDeque<>();
    private final Map<Long, Long> slotGenerations = new HashMap<>();
    private long nextHandleSlot;
    private SimulationInstant nextScheduleOrder = SimulationInstant.ZERO;
    private boolean dispatching;

    public Scheduler(HandlerRegistry handlers) {
        if (handlers == null) {
            throw new IllegalArgumentException("handlers must not be null");
        }
        this.handlers = handlers;
    }

    public TaskHandle schedule(long when, HandlerId handlerId, long processId) {
        if (when < 0) {
            throw new IllegalArgumentException("when must be >= 0");
        }
        if (handlerId == null) {
            throw new IllegalArgumentException("handlerId must not be null");
        }
        if (!handlers.contains(handlerId)) {
            throw new IllegalArgumentException("unknown handler: " + handlerId);
        }

        TaskHandle handle = allocateHandle();
        SimulationInstant scheduleOrder = nextScheduleOrder;
        nextScheduleOrder = nextScheduleOrder.plusTicks(1L);
        ScheduledTask task = new ScheduledTask(handle, when, handlerId, processId, scheduleOrder);
        queue.add(task);
        activeTasks.put(handle, task);
        return handle;
    }

    public boolean cancel(TaskHandle handle) {
        if (handle == null) {
            return false;
        }

        ScheduledTask task = activeTasks.remove(handle);
        if (task == null) {
            return false;
        }

        // If it has not yet been collected into the current due batch, remove it immediately.
        // If it is already in dueBatch, activeTasks no longer contains it and dispatchBatch skips it.
        queue.remove(task);
        releaseHandle(handle);
        return true;
    }

    public void dispatchDue(long now) {
        if (now < 0) {
            throw new IllegalArgumentException("now must be >= 0");
        }
        if (dispatching) {
            throw new IllegalStateException("scheduler is already dispatching");
        }

        dispatching = true;
        try {
            collectDue(now);
            dispatchBatch();
        } finally {
            dueBatch.clear();
            dispatching = false;
        }
    }

    /** Number of currently pending tasks, excluding historical work. */
    public int size() {
        return activeTasks.size();
    }

    /** Physical future-queue entries retained right now. Useful for longevity diagnostics. */
    public int queuedEntryCount() {
        return queue.size();
    }

    /** Handle slots retained for the current/peak working set, not one slot per historical task. */
    public int allocatedHandleSlotCount() {
        return slotGenerations.size();
    }

    private void collectDue(long now) {
        while (!queue.isEmpty() && queue.first().when() <= now) {
            dueBatch.add(queue.pollFirst());
        }
    }

    private void dispatchBatch() {
        DispatchException failure = null;

        for (int index = 0; index < dueBatch.size(); index++) {
            ScheduledTask task = dueBatch.get(index);
            ScheduledTask active = activeTasks.get(task.handle());
            if (active != task) {
                continue;
            }

            ScheduledHandler handler = handlers.get(task.handlerId());
            if (handler == null) {
                throw new IllegalStateException("handler is not registered: " + task.handlerId());
            }

            activeTasks.remove(task.handle());
            releaseHandle(task.handle());

            try {
                handler.handle(task.processId());
            } catch (RuntimeException exception) {
                if (failure == null) {
                    failure = new DispatchException(task, exception);
                } else {
                    failure.addFailure(task, exception);
                }
            }
        }

        if (failure != null) {
            throw failure;
        }
    }

    private TaskHandle allocateHandle() {
        if (!freeHandleSlots.isEmpty()) {
            long slot = freeHandleSlots.removeFirst();
            long generation = slotGenerations.get(slot);
            if (generation == Long.MAX_VALUE) {
                slotGenerations.remove(slot);
                return allocateFreshHandle();
            }
            generation++;
            slotGenerations.put(slot, generation);
            return TaskHandle.create(slot, generation);
        }
        return allocateFreshHandle();
    }

    private TaskHandle allocateFreshHandle() {
        if (nextHandleSlot == Long.MAX_VALUE) {
            throw new IllegalStateException("concurrent task handle space exhausted");
        }
        long slot = nextHandleSlot++;
        slotGenerations.put(slot, 0L);
        return TaskHandle.create(slot, 0L);
    }

    private void releaseHandle(TaskHandle handle) {
        Long currentGeneration = slotGenerations.get(handle.asLong());
        if (currentGeneration == null || currentGeneration.longValue() != handle.generation()) {
            return;
        }
        if (currentGeneration == Long.MAX_VALUE) {
            slotGenerations.remove(handle.asLong());
            return;
        }
        freeHandleSlots.addLast(handle.asLong());
    }

    public static final class DispatchException extends RuntimeException {

        private int failureCount = 1;

        private DispatchException(ScheduledTask task, RuntimeException cause) {
            super(message(task), cause);
        }

        public int failureCount() {
            return failureCount;
        }

        private void addFailure(ScheduledTask task, RuntimeException cause) {
            failureCount++;
            addSuppressed(new RuntimeException(message(task), cause));
        }

        private static String message(ScheduledTask task) {
            return "scheduled handler failed"
                    + ": handle=" + task.handle()
                    + ", handler=" + task.handlerId()
                    + ", processId=" + task.processId();
        }
    }
}
