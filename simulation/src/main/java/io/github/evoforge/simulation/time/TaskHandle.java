package io.github.evoforge.simulation.time;

public final class TaskHandle {

    private final long value;

    private TaskHandle(long value) {
        this.value = value;
    }

    public static TaskHandle of(long value) {
        if (value < 0) {
            throw new IllegalArgumentException(
                    "value must be >= 0");
        }

        return new TaskHandle(value);
    }

    public long asLong() {
        return value;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof TaskHandle other)) {
            return false;
        }

        return value == other.value;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(value);
    }

    @Override
    public String toString() {
        return "TaskHandle[" + value + "]";
    }
}