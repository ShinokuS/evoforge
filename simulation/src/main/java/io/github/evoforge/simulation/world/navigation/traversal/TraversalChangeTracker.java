package io.github.evoforge.simulation.world.navigation.traversal;

/**
 * Small traversal-domain change tracker. It stores only the newest coordinate;
 * consumers that miss more than one revision must conservatively invalidate globally.
 */
public final class TraversalChangeTracker
        implements TraversalChangeLookup {

    private long revision;
    private int lastChangeX;
    private int lastChangeY;
    private int lastChangeZ;

    @Override
    public long revision() {
        return revision;
    }

    @Override
    public int lastChangeX() {
        requireChange();
        return lastChangeX;
    }

    @Override
    public int lastChangeY() {
        requireChange();
        return lastChangeY;
    }

    @Override
    public int lastChangeZ() {
        requireChange();
        return lastChangeZ;
    }

    /** Mutation capability owned by the coordinated world mutation boundary. */
    public void changed(
            int x,
            int y,
            int z) {

        if (revision == Long.MAX_VALUE) {
            throw new IllegalStateException(
                    "traversal revision exhausted");
        }

        lastChangeX = x;
        lastChangeY = y;
        lastChangeZ = z;
        revision++;
    }

    private void requireChange() {
        if (revision == 0) {
            throw new IllegalStateException(
                    "no traversal change has been recorded");
        }
    }
}
