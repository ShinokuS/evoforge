package io.github.evoforge.visualizer.presentation.portal;

/** Bounded presentation scope for one cave/building/other covered structure. */
public record InteriorView(
        String id,
        String label,
        int minX,
        int maxX,
        int minY,
        int maxY,
        int minZ,
        int maxZ,
        int initialZ) {

    public InteriorView {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("label must not be blank");
        }
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("interior bounds must be ordered");
        }
        if (initialZ < minZ || initialZ > maxZ) {
            throw new IllegalArgumentException("initialZ must be inside interior bounds");
        }
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }
}
