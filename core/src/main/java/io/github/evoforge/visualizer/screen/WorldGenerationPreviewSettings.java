package io.github.evoforge.visualizer.screen;

import java.util.function.LongSupplier;

/** Mutable draft settings edited by the world-generation development UI. */
final class WorldGenerationPreviewSettings {
    static final int MIN_HORIZONTAL_DIMENSION = 32;
    static final int MAX_HORIZONTAL_DIMENSION = Integer.MAX_VALUE;

    private int width = 64;
    private int length = 64;
    private long seed = 1L;
    private boolean randomSeedOnGenerate;
    private int coveragePpm = 350_000;
    private int scalePpm = 750_000;
    private int fragmentationPpm = 250_000;
    private int reliefPpm = 600_000;
    private int localReliefPpm = 450_000;
    private int landformScalePpm = 500_000;
    private int ruggednessPpm = 350_000;
    private int mountainAbundancePpm = 350_000;
    private int mountainHeightPpm = 520_000;
    private int mountainScalePpm = 500_000;
    private int mountainChaininessPpm = 550_000;
    private int mountainSharpnessPpm = 600_000;
    private boolean mountainPlateausEnabled = true;
    private int mountainPlateauProbabilityPpm = 180_000;

    int width() { return width; }
    int length() { return length; }
    long seed() { return seed; }
    boolean randomSeedOnGenerate() { return randomSeedOnGenerate; }
    int coveragePpm() { return coveragePpm; }
    int scalePpm() { return scalePpm; }
    int fragmentationPpm() { return fragmentationPpm; }
    int reliefPpm() { return reliefPpm; }
    int localReliefPpm() { return localReliefPpm; }
    int landformScalePpm() { return landformScalePpm; }
    int ruggednessPpm() { return ruggednessPpm; }
    int mountainAbundancePpm() { return mountainAbundancePpm; }
    int mountainHeightPpm() { return mountainHeightPpm; }
    int mountainScalePpm() { return mountainScalePpm; }
    int mountainChaininessPpm() { return mountainChaininessPpm; }
    int mountainSharpnessPpm() { return mountainSharpnessPpm; }
    boolean mountainPlateausEnabled() { return mountainPlateausEnabled; }
    int mountainPlateauProbabilityPpm() { return mountainPlateauProbabilityPpm; }

    void width(int value) { width = requireDimension(value, "width"); }
    void length(int value) { length = requireDimension(value, "length"); }
    void seed(long value) { seed = value; }
    void randomSeedOnGenerate(boolean value) { randomSeedOnGenerate = value; }
    void coveragePpm(int value) { coveragePpm = requirePpm(value, "coveragePpm"); }
    void scalePpm(int value) { scalePpm = requirePpm(value, "scalePpm"); }
    void fragmentationPpm(int value) { fragmentationPpm = requirePpm(value, "fragmentationPpm"); }
    void reliefPpm(int value) { reliefPpm = requirePpm(value, "reliefPpm"); }
    void localReliefPpm(int value) { localReliefPpm = requirePpm(value, "localReliefPpm"); }
    void landformScalePpm(int value) { landformScalePpm = requirePpm(value, "landformScalePpm"); }
    void ruggednessPpm(int value) { ruggednessPpm = requirePpm(value, "ruggednessPpm"); }
    void mountainAbundancePpm(int value) { mountainAbundancePpm = requirePpm(value, "mountainAbundancePpm"); }
    void mountainHeightPpm(int value) { mountainHeightPpm = requirePpm(value, "mountainHeightPpm"); }
    void mountainScalePpm(int value) { mountainScalePpm = requirePpm(value, "mountainScalePpm"); }
    void mountainChaininessPpm(int value) { mountainChaininessPpm = requirePpm(value, "mountainChaininessPpm"); }
    void mountainSharpnessPpm(int value) { mountainSharpnessPpm = requirePpm(value, "mountainSharpnessPpm"); }
    void mountainPlateausEnabled(boolean value) { mountainPlateausEnabled = value; }
    void mountainPlateauProbabilityPpm(int value) {
        mountainPlateauProbabilityPpm = requirePpm(value, "mountainPlateauProbabilityPpm");
    }

    long prepareSeedForGeneration(LongSupplier randomSeedSource) {
        if (randomSeedSource == null) {
            throw new IllegalArgumentException("random seed source must not be null");
        }
        if (randomSeedOnGenerate) {
            seed = randomSeedSource.getAsLong();
        }
        return seed;
    }

    void nextSeed() {
        seed = seed == Long.MAX_VALUE ? Long.MIN_VALUE : seed + 1L;
    }

    long columnCount() { return (long) width * length; }
    int maxHorizontalDimension() { return Math.max(width, length); }

    WorldGenerationPreviewConfig snapshot() {
        return new WorldGenerationPreviewConfig(
                width,
                length,
                seed,
                coveragePpm,
                scalePpm,
                fragmentationPpm,
                reliefPpm,
                localReliefPpm,
                landformScalePpm,
                ruggednessPpm,
                mountainAbundancePpm,
                mountainHeightPpm,
                mountainScalePpm,
                mountainChaininessPpm,
                mountainSharpnessPpm,
                mountainPlateausEnabled,
                mountainPlateauProbabilityPpm);
    }

    private static int requireDimension(int value, String name) {
        if (value < MIN_HORIZONTAL_DIMENSION || value > MAX_HORIZONTAL_DIMENSION) {
            throw new IllegalArgumentException(
                    name + " must be at least " + MIN_HORIZONTAL_DIMENSION + " cells");
        }
        return value;
    }

    private static int requirePpm(int value, String name) {
        if (value < 0 || value > 1_000_000) {
            throw new IllegalArgumentException(name + " must be in [0, 1000000]");
        }
        return value;
    }
}
