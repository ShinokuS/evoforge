package io.github.evoforge.simulation.world.object;

public final class ObjectId {

    private static final long LOWER_32_BITS = 0xFFFF_FFFFL;

    private final long value;

    private ObjectId(long value) {
        this.value = value;
    }

    public static ObjectId of(int slot, int generation) {
        if (slot < 0) {
            throw new IllegalArgumentException("slot must be >= 0");
        }

        if (generation < 0) {
            throw new IllegalArgumentException("generation must be >= 0");
        }

        long value = ((long) generation << 32)
                | ((long) slot & LOWER_32_BITS);

        return new ObjectId(value);
    }

    public int slot() {
        return (int) (value & LOWER_32_BITS);
    }

    public int generation() {
        return (int) (value >>> 32);
    }

    public long asLong() {
        return value;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof ObjectId other)) {
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
        return "ObjectId[" + slot() + ":" + generation() + "]";
    }
}