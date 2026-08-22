package io.github.evoforge.simulation.world.geometry;

import io.github.evoforge.simulation.world.space.measurement.CellVolume;

/**
 * Neutral helpers for cell-local free-space geometry.
 *
 * <p>Local height uses the same deterministic normalized scale as {@link CellVolume}:
 * {@code 0} is the bottom of the cell and {@link #FULL_HEIGHT} is the top. Height
 * and volume are different quantities even though they intentionally share one
 * fixed-point resolution.
 */
public final class CellSpace {

    public static final int EMPTY_HEIGHT = 0;
    public static final int FULL_HEIGHT = CellVolume.FULL;

    /** Sentinel used when a Shape exposes no physical opening through a face. */
    public static final int CLOSED = FULL_HEIGHT + 1;

    private CellSpace() {
    }

    public static int requireHeight(
            int localHeight) {

        if (localHeight < EMPTY_HEIGHT
                || localHeight > FULL_HEIGHT) {
            throw new IllegalArgumentException(
                    "cell-local height must be in ["
                            + EMPTY_HEIGHT
                            + ", "
                            + FULL_HEIGHT
                            + "]: "
                            + localHeight);
        }

        return localHeight;
    }

    public static int requireOpeningFloor(
            int localHeight) {

        if (localHeight == CLOSED) {
            return localHeight;
        }
        return requireHeight(localHeight);
    }

    /**
     * Returns free geometric volume below a local height.
     *
     * <p>A coordinate without a Shape is fully open space, so its free volume below
     * height {@code h} is exactly {@code h} on the normalized fixed-point scale.
     */
    public static int freeVolumeBelow(
            Shape shape,
            int localHeight) {

        int height = requireHeight(localHeight);
        if (shape == null) {
            return height;
        }

        return CellVolume.requireValid(
                shape.freeVolumeBelow(height));
    }

    public static int capacity(
            Shape shape) {

        return freeVolumeBelow(
                shape,
                FULL_HEIGHT);
    }

    /**
     * Returns the lowest local elevation at which a physical connection through the
     * requested face begins, or {@link #CLOSED} when no such connection exists.
     *
     * <p>The value is deliberately a coarse first-order boundary description. It is
     * objective geometry, not a navigation port and not a fluid-specific rule.
     */
    public static int boundaryOpeningFloor(
            Shape shape,
            CellFace face) {

        if (face == null) {
            throw new IllegalArgumentException(
                    "face must not be null");
        }

        if (shape == null) {
            return switch (face) {
                case POSITIVE_Z -> FULL_HEIGHT;
                case NEGATIVE_X,
                        POSITIVE_X,
                        NEGATIVE_Y,
                        POSITIVE_Y,
                        NEGATIVE_Z -> EMPTY_HEIGHT;
            };
        }

        return requireOpeningFloor(
                shape.boundaryOpeningFloor(face));
    }

    /**
     * Inverts {@link #freeVolumeBelow(Shape, int)} for a valid amount that fits the
     * cell's current free-space capacity.
     */
    public static int surfaceHeight(
            Shape shape,
            int amount) {

        int volume = CellVolume.requireValid(amount);
        int capacity = capacity(shape);
        if (volume > capacity) {
            throw new IllegalArgumentException(
                    "volume exceeds current cell-space capacity: "
                            + volume
                            + " > "
                            + capacity);
        }
        if (volume == CellVolume.EMPTY) {
            return EMPTY_HEIGHT;
        }

        int low = EMPTY_HEIGHT;
        int high = FULL_HEIGHT;

        while (low < high) {
            int middle = low + ((high - low) >>> 1);
            if (freeVolumeBelow(shape, middle) >= volume) {
                high = middle;
            } else {
                low = middle + 1;
            }
        }

        return low;
    }
}
