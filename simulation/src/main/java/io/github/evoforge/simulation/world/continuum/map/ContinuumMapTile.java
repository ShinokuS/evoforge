package io.github.evoforge.simulation.world.continuum.map;

import java.util.Arrays;

/** Immutable scalar image used only as a derived map representation. */
public final class ContinuumMapTile {
    private final ContinuumMapTileKey key;
    private final int sampleSide;
    private final byte[] luminance;

    /** Public boundary defensively copies caller-owned memory. */
    public ContinuumMapTile(ContinuumMapTileKey key, int sampleSide, byte[] luminance) {
        this(key, sampleSide, luminance, true);
    }

    /**
     * Internal generator path: the fresh buffer is transferred into this immutable object and must
     * never be retained or mutated by the caller afterwards.
     */
    static ContinuumMapTile fromOwnedLuminance(ContinuumMapTileKey key, int sampleSide, byte[] luminance) {
        return new ContinuumMapTile(key, sampleSide, luminance, false);
    }

    private ContinuumMapTile(ContinuumMapTileKey key, int sampleSide, byte[] luminance, boolean copy) {
        if (key == null) throw new IllegalArgumentException("key must not be null");
        if (sampleSide <= 0) throw new IllegalArgumentException("sampleSide must be > 0");
        if (luminance == null) throw new IllegalArgumentException("luminance must not be null");
        if (luminance.length != Math.multiplyExact(sampleSide, sampleSide)) {
            throw new IllegalArgumentException("luminance length does not match sampleSide");
        }
        this.key = key;
        this.sampleSide = sampleSide;
        this.luminance = copy ? Arrays.copyOf(luminance, luminance.length) : luminance;
    }

    public ContinuumMapTileKey key() {
        return key;
    }

    public int sampleSide() {
        return sampleSide;
    }

    public int payloadBytes() {
        return luminance.length;
    }

    public int luminanceUnsigned(int x, int y) {
        if (x < 0 || x >= sampleSide || y < 0 || y >= sampleSide) {
            throw new IndexOutOfBoundsException("sample outside tile");
        }
        return Byte.toUnsignedInt(luminance[y * sampleSide + x]);
    }

    public byte[] copyLuminance() {
        return Arrays.copyOf(luminance, luminance.length);
    }
}
