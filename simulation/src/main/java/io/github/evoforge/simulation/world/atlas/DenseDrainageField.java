package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.spatial.WorldBounds;

final class DenseDrainageField implements DrainageField {
    static final int TERMINAL = -1;

    private final WorldBounds bounds;
    private final int width;
    private final int[] downstream;
    private final long[] contributingArea;
    private final int[] terminal;

    /** Copy-safe constructor for callers that retain ownership of their input arrays. */
    DenseDrainageField(
            WorldBounds bounds,
            int[] downstream,
            long[] contributingArea,
            int[] terminal) {
        this(bounds, downstream, contributingArea, terminal, true);
    }

    /**
     * Transfers exclusive ownership of freshly produced drainage buffers without cloning them.
     *
     * <p>The caller must never mutate any supplied array after this call.</p>
     */
    static DenseDrainageField takeOwnership(
            WorldBounds bounds,
            int[] downstream,
            long[] contributingArea,
            int[] terminal) {
        return new DenseDrainageField(bounds, downstream, contributingArea, terminal, false);
    }

    private DenseDrainageField(
            WorldBounds bounds,
            int[] downstream,
            long[] contributingArea,
            int[] terminal,
            boolean copyArrays) {
        if (bounds == null) {
            throw new IllegalArgumentException("bounds must not be null");
        }
        if (downstream == null || contributingArea == null || terminal == null) {
            throw new IllegalArgumentException("drainage arrays must not be null");
        }
        int expected = cellCount(bounds);
        if (downstream.length != expected
                || contributingArea.length != expected
                || terminal.length != expected) {
            throw new IllegalArgumentException(
                    "drainage arrays must match horizontal world area: " + expected);
        }
        for (int index = 0; index < expected; index++) {
            int next = downstream[index];
            if (next < TERMINAL || next >= expected || next == index) {
                throw new IllegalArgumentException("invalid downstream index: " + next);
            }
            if (contributingArea[index] < 1L) {
                throw new IllegalArgumentException("contributing area must be >= 1");
            }
            if (terminal[index] < 0 || terminal[index] >= expected) {
                throw new IllegalArgumentException("invalid terminal index: " + terminal[index]);
            }
        }
        this.bounds = bounds;
        this.width = Math.toIntExact((long) bounds.maxX() - bounds.minX() + 1L);
        this.downstream = copyArrays ? downstream.clone() : downstream;
        this.contributingArea = copyArrays ? contributingArea.clone() : contributingArea;
        this.terminal = copyArrays ? terminal.clone() : terminal;
    }

    @Override
    public WorldBounds bounds() {
        return bounds;
    }

    @Override
    public boolean hasDownstream(int x, int y) {
        return downstream[indexOf(x, y)] != TERMINAL;
    }

    @Override
    public int downstreamXAt(int x, int y) {
        int next = requireDownstream(indexOf(x, y), x, y);
        return bounds.minX() + next % width;
    }

    @Override
    public int downstreamYAt(int x, int y) {
        int next = requireDownstream(indexOf(x, y), x, y);
        return bounds.minY() + next / width;
    }

    @Override
    public long contributingAreaAt(int x, int y) {
        return contributingArea[indexOf(x, y)];
    }

    @Override
    public int terminalXAt(int x, int y) {
        int sink = terminal[indexOf(x, y)];
        return bounds.minX() + sink % width;
    }

    @Override
    public int terminalYAt(int x, int y) {
        int sink = terminal[indexOf(x, y)];
        return bounds.minY() + sink / width;
    }

    private int indexOf(int x, int y) {
        if (!contains(x, y)) {
            throw new IllegalArgumentException(
                    "position outside drainage field: (" + x + ", " + y + ")");
        }
        return (y - bounds.minY()) * width + (x - bounds.minX());
    }

    private int requireDownstream(int index, int x, int y) {
        int next = downstream[index];
        if (next == TERMINAL) {
            throw new IllegalStateException(
                    "terminal drainage column has no downstream target: (" + x + ", " + y + ")");
        }
        return next;
    }

    private static int cellCount(WorldBounds bounds) {
        long width = (long) bounds.maxX() - bounds.minX() + 1L;
        long height = (long) bounds.maxY() - bounds.minY() + 1L;
        long area;
        try {
            area = Math.multiplyExact(width, height);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "horizontal world area exceeds current drainage representation", exception);
        }
        if (area > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "horizontal world area exceeds current drainage representation: " + area);
        }
        return (int) area;
    }
}
