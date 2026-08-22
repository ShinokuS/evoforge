package io.github.evoforge.simulation.world.space;

/** Inclusive finite integer bounds for one simulation world. */
public record WorldBounds(
        int minX,
        int maxX,
        int minY,
        int maxY,
        int minZ,
        int maxZ) {

    public WorldBounds {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException(
                    "world bounds must be ordered on every axis");
        }
    }

    public boolean contains(
            int x,
            int y,
            int z) {

        return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }
}
