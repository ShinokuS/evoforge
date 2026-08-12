package io.github.evoforge.visualizer.presentation;

/** Optional cardinal world-space direction used by Shape debug presentation. */
public record ShapeDirectionDiagnostic(
        boolean visible,
        int x,
        int y) {

    public static final ShapeDirectionDiagnostic NONE =
            new ShapeDirectionDiagnostic(false, 0, 0);

    public ShapeDirectionDiagnostic {
        if (visible && Math.abs(x) + Math.abs(y) != 1) {
            throw new IllegalArgumentException(
                    "visible Shape diagnostic must be cardinal");
        }
        if (!visible && (x != 0 || y != 0)) {
            throw new IllegalArgumentException(
                    "hidden Shape diagnostic must have zero direction");
        }
    }

    public static ShapeDirectionDiagnostic cardinal(
            int x,
            int y) {

        return new ShapeDirectionDiagnostic(true, x, y);
    }
}
