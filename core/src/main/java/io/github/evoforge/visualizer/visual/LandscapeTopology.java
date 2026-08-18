package io.github.evoforge.visualizer.visual;

/** Pure presentation-side topology helpers for cell-aligned landscape art. */
public final class LandscapeTopology {

    public static final int N = 1 << 0;
    public static final int NE = 1 << 1;
    public static final int E = 1 << 2;
    public static final int SE = 1 << 3;
    public static final int S = 1 << 4;
    public static final int SW = 1 << 5;
    public static final int W = 1 << 6;
    public static final int NW = 1 << 7;

    private LandscapeTopology() {
    }

    /**
     * Normalizes diagonal connectivity using standard corner gating.
     * A diagonal only affects the tile when both adjacent cardinal neighbours
     * are connected. This keeps inner corners expressive without allowing a
     * diagonal-only neighbour to erase an exposed edge.
     */
    public static int normalize(int mask) {
        int normalized = mask & 0xFF;

        if (!contains(normalized, N) || !contains(normalized, E)) {
            normalized &= ~NE;
        }
        if (!contains(normalized, S) || !contains(normalized, E)) {
            normalized &= ~SE;
        }
        if (!contains(normalized, S) || !contains(normalized, W)) {
            normalized &= ~SW;
        }
        if (!contains(normalized, N) || !contains(normalized, W)) {
            normalized &= ~NW;
        }

        return normalized;
    }

    /**
     * Canonical topology for the exposed top surface of a full terrain cell.
     *
     * <p>Earth is a face-boundary cue. If two adjacent cardinal faces are already visually joined,
     * a missing diagonal neighbour touches only at one point and must not create a tiny isolated
     * earth "inner corner". This is especially important where a neighbouring Shape owns the
     * shared visual boundary, such as a ramp meeting an ordinary surface.</p>
     */
    public static int normalizeSurfaceArt(int mask) {
        int normalized = normalize(mask);
        if (contains(normalized, N) && contains(normalized, E)) normalized |= NE;
        if (contains(normalized, S) && contains(normalized, E)) normalized |= SE;
        if (contains(normalized, S) && contains(normalized, W)) normalized |= SW;
        if (contains(normalized, N) && contains(normalized, W)) normalized |= NW;
        return normalized;
    }

    /** Returns a stable visual variant for one world cell. */
    public static int variant(
            int x,
            int y,
            int z,
            int variantCount) {

        if (variantCount <= 0) {
            throw new IllegalArgumentException("variantCount must be positive");
        }

        int hash = x * 0x9E3779B9;
        hash ^= y * 0x85EBCA6B;
        hash ^= z * 0xC2B2AE35;
        hash ^= hash >>> 16;
        hash *= 0x7FEB352D;
        hash ^= hash >>> 15;
        hash *= 0x846CA68B;
        hash ^= hash >>> 16;

        return Math.floorMod(hash, variantCount);
    }

    public static boolean contains(
            int mask,
            int bit) {

        return (mask & bit) != 0;
    }
}
