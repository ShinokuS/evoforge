package io.github.evoforge.visualizer.presentation.portal;

/**
 * Presentation-only marker connecting one surface entrance to a bounded interior view.
 * Simulation coordinates remain authoritative on both sides; entering a portal changes
 * only what the user sees, never object state or pathfinding topology.
 */
public record ViewPortal(
        String id,
        ViewPortalKind kind,
        String label,
        int surfaceX,
        int surfaceY,
        int surfaceZ,
        int interiorX,
        int interiorY,
        int interiorZ,
        InteriorView interior) {

    public ViewPortal {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (kind == null) {
            throw new IllegalArgumentException("kind must not be null");
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("label must not be blank");
        }
        if (interior == null) {
            throw new IllegalArgumentException("interior must not be null");
        }
        if (!interior.contains(interiorX, interiorY, interiorZ)) {
            throw new IllegalArgumentException("interior entry must be inside interior view");
        }
    }

    public boolean isSurfaceAt(int x, int y) {
        return surfaceX == x && surfaceY == y;
    }

    public boolean isInteriorAt(String interiorId, int x, int y, int z) {
        return interior.id().equals(interiorId)
                && interiorX == x && interiorY == y && interiorZ == z;
    }
}
