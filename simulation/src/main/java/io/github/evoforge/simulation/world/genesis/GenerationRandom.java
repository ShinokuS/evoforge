package io.github.evoforge.simulation.world.genesis;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Pure deterministic random sampler for world generation.
 *
 * <p>Samples are addressed by semantic stage and purpose plus three stable scope coordinates and an
 * ordinal. Direct cell-scoped generation uses global XYZ; macro stages may use their own stable
 * lattice coordinates. Call order is not part of the random state.</p>
 *
 * <p>Hot generation code should normally {@link #bind(GenerationStageId, GenerationPurposeId)} a
 * semantic stream once and reuse the returned sampler. Binding only precomputes the master-seed,
 * stage and purpose prefix; coordinate and ordinal mixing is exactly the same as in
 * {@link #sampleLong(GenerationStageId, GenerationPurposeId, long, long, long, long)}.</p>
 */
public final class GenerationRandom {
    private static final long SEED_SALT = 0x243f6a8885a308d3L;
    private static final long STAGE_SALT = 0x13198a2e03707344L;
    private static final long PURPOSE_SALT = 0xa4093822299f31d0L;
    private static final long X_SALT = 0x082efa98ec4e6c89L;
    private static final long Y_SALT = 0x452821e638d01377L;
    private static final long Z_SALT = 0xbe5466cf34e90c6cL;
    private static final long ORDINAL_SALT = 0xc0ac29b7c97c50ddL;
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    /*
     * Stage/purpose identifiers are a tiny semantic vocabulary reused millions of times during
     * generation. Cache their exact FNV-64 value once so direct semantic sampling does not repeatedly
     * allocate UTF-8 byte arrays and re-hash the same strings. The cache changes only computation
     * cost: the mixed RNG state remains bit-identical to the uncached definition.
     */
    private static final ConcurrentMap<String, Long> SEMANTIC_HASHES = new ConcurrentHashMap<>();

    private final long masterSeed;
    private final long seededState;
    private final ConcurrentMap<GenerationStageId, ConcurrentMap<GenerationPurposeId, Long>>
            semanticStates = new ConcurrentHashMap<>();

    private GenerationRandom(long masterSeed) {
        this.masterSeed = masterSeed;
        this.seededState = mix64(masterSeed ^ SEED_SALT);
    }

    public static GenerationRandom from(WorldGenesis genesis) {
        if (genesis == null) {
            throw new IllegalArgumentException("genesis must not be null");
        }
        if (!RngRevision.V1.equals(genesis.rngRevision())) {
            throw new IllegalArgumentException(
                    "unsupported RNG revision: " + genesis.rngRevision().value());
        }
        return new GenerationRandom(genesis.masterSeed());
    }

    /**
     * Binds the invariant semantic prefix of a generation random stream.
     *
     * <p>The returned sampler is immutable, stateless and safe to share between deterministic worker
     * tiles. Sampling order is irrelevant.</p>
     */
    public BoundSampler bind(GenerationStageId stage, GenerationPurposeId purpose) {
        return new BoundSampler(semanticState(stage, purpose));
    }

    public long sampleLong(
            GenerationStageId stage,
            GenerationPurposeId purpose,
            long x,
            long y,
            long z,
            long ordinal) {
        if (ordinal < 0) {
            throw new IllegalArgumentException("ordinal must be >= 0");
        }
        return sampleFromState(semanticState(stage, purpose), x, y, z, ordinal);
    }

    private long semanticState(GenerationStageId stage, GenerationPurposeId purpose) {
        if (stage == null) {
            throw new IllegalArgumentException("stage must not be null");
        }
        if (purpose == null) {
            throw new IllegalArgumentException("purpose must not be null");
        }
        ConcurrentMap<GenerationPurposeId, Long> purposeStates =
                semanticStates.computeIfAbsent(stage, ignored -> new ConcurrentHashMap<>());
        return purposeStates.computeIfAbsent(
                purpose,
                key -> computeSemanticState(stage, key));
    }

    private long computeSemanticState(
            GenerationStageId stage,
            GenerationPurposeId purpose) {
        long stageState = mix64(seededState ^ stableStringHash(stage.value()) ^ STAGE_SALT);
        return mix64(stageState ^ stableStringHash(purpose.value()) ^ PURPOSE_SALT);
    }

    /** A stateless deterministic sampler with master seed, stage and purpose already bound. */
    public static final class BoundSampler {
        private final long semanticState;

        private BoundSampler(long semanticState) {
            this.semanticState = semanticState;
        }

        public long sampleLong(long x, long y, long z, long ordinal) {
            if (ordinal < 0) {
                throw new IllegalArgumentException("ordinal must be >= 0");
            }
            return sampleFromState(semanticState, x, y, z, ordinal);
        }
    }

    private static long sampleFromState(
            long semanticState,
            long x,
            long y,
            long z,
            long ordinal) {
        long state = mix64(semanticState ^ x ^ X_SALT);
        state = mix64(state ^ y ^ Y_SALT);
        state = mix64(state ^ z ^ Z_SALT);
        return mix64(state ^ ordinal ^ ORDINAL_SALT);
    }

    private static long stableStringHash(String value) {
        return SEMANTIC_HASHES.computeIfAbsent(value, GenerationRandom::computeStableStringHash);
    }

    private static long computeStableStringHash(String value) {
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
