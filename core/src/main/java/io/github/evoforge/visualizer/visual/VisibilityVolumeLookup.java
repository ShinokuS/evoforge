package io.github.evoforge.visualizer.visual;

/**
 * Presentation-side geometric visibility view of world volume.
 *
 * <p>The resolver depends on capabilities rather than terrain/object types so
 * future walls, roofs or large objects can contribute through another adapter
 * without changing the cutaway algorithm.</p>
 */
public interface VisibilityVolumeLookup {

    boolean solid(int x, int y, int z);

    boolean opaque(int x, int y, int z);

    boolean empty();

    int minOccupiedZ();

    int maxOccupiedZ();

    /** Monotonic version used to invalidate presentation caches safely. */
    long revision();
}
