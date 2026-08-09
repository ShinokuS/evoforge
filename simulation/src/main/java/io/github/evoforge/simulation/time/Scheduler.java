package io.github.evoforge.simulation.time;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

public final class Scheduler {

    private static final Comparator<ScheduledTask> ORDER = Comparator.comparingLong(ScheduledTask::when)
            .thenComparingLong(
                    task -> task.handle().asLong());

    private final HandlerRegistry handlers;

    private final PriorityQueue<ScheduledTask> queue = new PriorityQueue<>(ORDER);

    private final Map<TaskHandle, ScheduledTask> activeTasks = new HashMap<>();

    private final List<ScheduledTask> dueBatch = new ArrayList<>();

    private long nextHandle;
    private boolean dispatching;

    public Scheduler(HandlerRegistry handlers) {
        if (handlers == null) {
            throw new IllegalArgumentException(
                    "handlers must not be null");
        }

        this.handlers = handlers;
    }

    public TaskHandle schedule(
            long when,
            HandlerId handlerId,
            long processId) {

        if (when < 0) {
            throw new IllegalArgumentException(
                    "when must be >= 0");
        }

        if (handlerId == null) {
            throw new IllegalArgumentException(
                    "handlerId must not be null");
        }

        if (!handlers.contains(handlerId)) {
            throw new IllegalArgumentException(
                    "unknown handler: " + handlerId);
        }

        if (nextHandle == Long.MAX_VALUE) {
            throw new IllegalStateException(
                    "task handle space exhausted");
        }

        TaskHandle handle = TaskHandle.of(nextHandle++);

        ScheduledTask task = new ScheduledTask(
                handle,
                when,
                handlerId,
                processId);

        queue.add(task);
        activeTasks.put(handle, task);

        return handle;
    }

    public boolean cancel(TaskHandle handle) {
        if (handle == null) {
            return false;
        }

        return activeTasks.remove(handle) != null;
    }

    public void dispatchDue(long now) {
        if (now < 0) {
            throw new IllegalArgumentException(
                    "now must be >= 0");
        }

        if (dispatching) {
            throw new IllegalStateException(
                    "scheduler is already dispatching");
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

    public int size() {
        return activeTasks.size();
    }

    private void collectDue(long now) {
        while (!queue.isEmpty()
                && queue.peek().when() <= now) {

            dueBatch.add(queue.poll());
        }
    }

    private void dispatchBatch() {
        for (int index = 0; index < dueBatch.size(); index++) {

            ScheduledTask task = dueBatch.get(index);

            ScheduledTask active = activeTasks.get(
                    task.handle());

            if (active != task) {
                continue;
            }

            activeTasks.remove(
                    task.handle());

            ScheduledHandler handler = handlers.get(
                    task.handlerId());

            if (handler == null) {
                throw new IllegalStateException(
                        "handler is not registered: "
                                + task.handlerId());
            }

            handler.handle(
                    task.processId());
        }
    }
}