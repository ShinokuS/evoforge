package io.github.evoforge.simulation.world.pathfinding;

/** Immutable disposable route. Steps exclude the source and include the goal. */
public final class PathRoute {

    private final int sourceX;
    private final int sourceY;
    private final int sourceZ;
    private final int goalX;
    private final int goalY;
    private final int goalZ;
    private final long totalCostUnits;
    private final int[] xs;
    private final int[] ys;
    private final int[] zs;

    PathRoute(
            int sourceX,
            int sourceY,
            int sourceZ,
            int goalX,
            int goalY,
            int goalZ,
            long totalCostUnits,
            int[] xs,
            int[] ys,
            int[] zs) {

        if (totalCostUnits < 0) {
            throw new IllegalArgumentException(
                    "totalCostUnits must be >= 0");
        }
        if (xs == null || ys == null || zs == null) {
            throw new IllegalArgumentException(
                    "route coordinate arrays must not be null");
        }
        if (xs.length != ys.length || xs.length != zs.length) {
            throw new IllegalArgumentException(
                    "route coordinate arrays must have equal length");
        }

        this.sourceX = sourceX;
        this.sourceY = sourceY;
        this.sourceZ = sourceZ;
        this.goalX = goalX;
        this.goalY = goalY;
        this.goalZ = goalZ;
        this.totalCostUnits = totalCostUnits;
        this.xs = xs;
        this.ys = ys;
        this.zs = zs;
    }

    static PathRoute empty(
            int x,
            int y,
            int z) {

        return new PathRoute(
                x, y, z,
                x, y, z,
                0L,
                new int[0],
                new int[0],
                new int[0]);
    }

    public int sourceX() {
        return sourceX;
    }

    public int sourceY() {
        return sourceY;
    }

    public int sourceZ() {
        return sourceZ;
    }

    public int goalX() {
        return goalX;
    }

    public int goalY() {
        return goalY;
    }

    public int goalZ() {
        return goalZ;
    }

    public long totalCostUnits() {
        return totalCostUnits;
    }

    public int size() {
        return xs.length;
    }

    public int x(int index) {
        return xs[index];
    }

    public int y(int index) {
        return ys[index];
    }

    public int z(int index) {
        return zs[index];
    }
}
