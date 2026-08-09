package io.github.evoforge.simulation.time;

final class ScheduledTask {

    private final TaskHandle handle;
    private final long when;
    private final HandlerId handlerId;
    private final long processId;

    ScheduledTask(
            TaskHandle handle,
            long when,
            HandlerId handlerId,
            long processId) {

        if (handle == null) {
            throw new IllegalArgumentException(
                    "handle must not be null");
        }

        if (when < 0) {
            throw new IllegalArgumentException(
                    "when must be >= 0");
        }

        if (handlerId == null) {
            throw new IllegalArgumentException(
                    "handlerId must not be null");
        }

        this.handle = handle;
        this.when = when;
        this.handlerId = handlerId;
        this.processId = processId;
    }

    TaskHandle handle() {
        return handle;
    }

    long when() {
        return when;
    }

    HandlerId handlerId() {
        return handlerId;
    }

    long processId() {
        return processId;
    }
}