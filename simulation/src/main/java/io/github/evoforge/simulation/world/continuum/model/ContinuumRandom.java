package io.github.evoforge.simulation.world.continuum.model;

import java.nio.charset.StandardCharsets;

/** Stateless coordinate-addressed random source for Continuum. Sampling order is never state. */
public final class ContinuumRandom {
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;
    private final long seed;

    public ContinuumRandom(long seed) {
        this.seed = seed;
    }

    public long sampleLong(String purpose, long x, long y, long ordinal) {
        if (purpose == null || purpose.isBlank()) {
            throw new IllegalArgumentException("purpose must not be blank");
        }
        if (ordinal < 0L) {
            throw new IllegalArgumentException("ordinal must be >= 0");
        }
        long state = mix64(seed ^ stableHash(purpose));
        state = mix64(state ^ x ^ 0x082efa98ec4e6c89L);
        state = mix64(state ^ y ^ 0x452821e638d01377L);
        return mix64(state ^ ordinal ^ 0xc0ac29b7c97c50ddL);
    }

    public double sampleUnit(String purpose, long x, long y, long ordinal) {
        return (sampleLong(purpose, x, y, ordinal) >>> 11) * 0x1.0p-53;
    }

    private static long stableHash(String value) {
        long hash = FNV_OFFSET_BASIS;
        for (byte item : value.getBytes(StandardCharsets.UTF_8)) {
            hash ^= item & 0xffL;
            hash *= FNV_PRIME;
        }
        return hash;
    }

    private static long mix64(long value) {
        long mixed = value;
        mixed = (mixed ^ (mixed >>> 30)) * 0xbf58476d1ce4e5b9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94d049bb133111ebL;
        return mixed ^ (mixed >>> 31);
    }
}
