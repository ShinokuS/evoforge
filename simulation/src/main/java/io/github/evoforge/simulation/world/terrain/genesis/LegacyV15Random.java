package io.github.evoforge.simulation.world.terrain.genesis;

import java.nio.charset.StandardCharsets;

/** Bit-compatible coordinate-addressed random sampler from the accepted V12-V15 generator. */
public final class LegacyV15Random {
    private static final long SEED_SALT = 0x243f6a8885a308d3L;
    private static final long STAGE_SALT = 0x13198a2e03707344L;
    private static final long PURPOSE_SALT = 0xa4093822299f31d0L;
    private static final long X_SALT = 0x082efa98ec4e6c89L;
    private static final long Y_SALT = 0x452821e638d01377L;
    private static final long Z_SALT = 0xbe5466cf34e90c6cL;
    private static final long ORDINAL_SALT = 0xc0ac29b7c97c50ddL;
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;
    private static final String ELEVATION_STAGE = "world:elevation";

    private final long masterSeed;

    public LegacyV15Random(long masterSeed) {
        this.masterSeed = masterSeed;
    }

    public long sampleElevation(String purpose, long x, long y, long ordinal) {
        return sample(ELEVATION_STAGE, purpose, x, y, 0L, ordinal);
    }

    public long sample(
            String stage,
            String purpose,
            long x,
            long y,
            long z,
            long ordinal) {
        if (stage == null || stage.isBlank()) {
            throw new IllegalArgumentException("stage must not be blank");
        }
        if (purpose == null || purpose.isBlank()) {
            throw new IllegalArgumentException("purpose must not be blank");
        }
        if (ordinal < 0L) {
            throw new IllegalArgumentException("ordinal must be >= 0");
        }

        long state = mix64(masterSeed ^ SEED_SALT);
        state = mix64(state ^ stableStringHash(stage) ^ STAGE_SALT);
        state = mix64(state ^ stableStringHash(purpose) ^ PURPOSE_SALT);
        state = mix64(state ^ x ^ X_SALT);
        state = mix64(state ^ y ^ Y_SALT);
        state = mix64(state ^ z ^ Z_SALT);
        return mix64(state ^ ordinal ^ ORDINAL_SALT);
    }

    private static long stableStringHash(String value) {
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
