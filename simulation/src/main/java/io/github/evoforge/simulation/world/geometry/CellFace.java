package io.github.evoforge.simulation.world.geometry;

/**
 * One of the six physical faces of a discrete world cell.
 *
 * <p>This is neutral geometry. It does not imply navigation, traversal permission,
 * fluid behavior or any other consumer-specific meaning.
 */
public enum CellFace {
    NEGATIVE_X(-1, 0, 0),
    POSITIVE_X(1, 0, 0),
    NEGATIVE_Y(0, -1, 0),
    POSITIVE_Y(0, 1, 0),
    NEGATIVE_Z(0, 0, -1),
    POSITIVE_Z(0, 0, 1);

    private final int dx;
    private final int dy;
    private final int dz;

    CellFace(
            int dx,
            int dy,
            int dz) {

        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
    }

    public int dx() {
        return dx;
    }

    public int dy() {
        return dy;
    }

    public int dz() {
        return dz;
    }

    public CellFace opposite() {
        return switch (this) {
            case NEGATIVE_X -> POSITIVE_X;
            case POSITIVE_X -> NEGATIVE_X;
            case NEGATIVE_Y -> POSITIVE_Y;
            case POSITIVE_Y -> NEGATIVE_Y;
            case NEGATIVE_Z -> POSITIVE_Z;
            case POSITIVE_Z -> NEGATIVE_Z;
        };
    }

    public static CellFace fromDelta(
            int dx,
            int dy,
            int dz) {

        for (CellFace face : values()) {
            if (face.dx == dx
                    && face.dy == dy
                    && face.dz == dz) {
                return face;
            }
        }

        throw new IllegalArgumentException(
                "cell-face delta must be one cardinal unit step: ("
                        + dx
                        + ", "
                        + dy
                        + ", "
                        + dz
                        + ")");
    }
}
