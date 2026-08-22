package io.github.evoforge.simulation.kernel.scheduling;

/**
 * Handle of one currently scheduled task.
 *
 * <p>The public numeric value is a reusable slot. A private generation protects callers from an
 * old handle accidentally cancelling a later task that reused the same slot.</p>
 */
public final class TaskHandle {

    private final long value;
    private final long generation;

    private TaskHandle(long value, long generation) {
        this.value = value;
        this.generation = generation;
    }

    public static TaskHandle of(long value) {
        return create(value, 0L);
    }

    static TaskHandle create(long value, long generation) {
        if (value < 0) {
            throw new IllegalArgumentException("value must be >= 0");
        }
        if (generation < 0) {
            throw new IllegalArgumentException("generation must be >= 0");
        }
        return new TaskHandle(value, generation);
    }

    public long asLong() {
        return value;
    }

    long generation() {
        return generation;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof TaskHandle other)) {
            return false;
        }
        return value == other.value && generation == other.generation;
    }

    @Override
    public int hashCode() {
        int result = Long.hashCode(value);
        result = 31 * result + Long.hashCode(generation);
        return result;
    }

    @Override
    public String toString() {
        return "TaskHandle[" + value + ":" + generation + "]";
    }
}
