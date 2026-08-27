package io.github.evoforge.simulation.world.terrain.genesis;

import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;

/**
 * Exact V12 global land-rank decision without retaining the old {@code long[worldArea]} sort.
 *
 * <p>The accepted V12 potential is only 16-bit. A fixed 65,536-bin histogram therefore reproduces
 * the same descending potential order, while one second streaming pass reproduces the old stable
 * row-major tie break. Optional V14 constraint uses the exact old 900k silhouette blend.</p>
 */
public final class V12LandRankPlan {
    private final V15TerrainCoordinateFrame frame;
    private final LegacyV15Random random;
    private final V12TerrainCalibration calibration;
    private final V12TerrainRecipe recipe;
    private final V14LandmassPlan silhouette;
    private final int thresholdPotential;
    private final long thresholdLastCellIndex;
    private final long landCount;

    private V12LandRankPlan(
            V15TerrainCoordinateFrame frame,
            LegacyV15Random random,
            V12TerrainCalibration calibration,
            V12TerrainRecipe recipe,
            V14LandmassPlan silhouette,
            int thresholdPotential,
            long thresholdLastCellIndex,
            long landCount) {
        this.frame = frame;
        this.random = random;
        this.calibration = calibration;
        this.recipe = recipe;
        this.silhouette = silhouette;
        this.thresholdPotential = thresholdPotential;
        this.thresholdLastCellIndex = thresholdLastCellIndex;
        this.landCount = landCount;
    }

    public static V12LandRankPlan prepareUnconstrained(
            ContinuumWorldDomain domain,
            long seed,
            V12TerrainCalibration calibration,
            V12TerrainRecipe recipe) {
        return prepare(domain, seed, calibration, recipe, null);
    }

    public static V12LandRankPlan prepareConstrained(
            ContinuumWorldDomain domain,
            long seed,
            V12TerrainCalibration calibration,
            V12TerrainRecipe recipe,
            V14LandmassPlan silhouette) {
        if (silhouette == null) {
            throw new IllegalArgumentException("V14 silhouette must not be null");
        }
        if (!domain.equals(silhouette.domain())) {
            throw new IllegalArgumentException("V14 silhouette must match the V12 Continuum domain");
        }
        return prepare(domain, seed, calibration, recipe, silhouette);
    }

    private static V12LandRankPlan prepare(
            ContinuumWorldDomain domain,
            long seed,
            V12TerrainCalibration calibration,
            V12TerrainRecipe recipe,
            V14LandmassPlan silhouette) {
        if (domain == null || calibration == null || recipe == null) {
            throw new IllegalArgumentException("V12 land-rank inputs must not be null");
        }
        if (domain.width() != calibration.width() || domain.height() != calibration.height()) {
            throw new IllegalArgumentException("V12 calibration must match its Continuum domain");
        }
        V15TerrainCoordinateFrame frame = V15TerrainCoordinateFrame.centered(domain);
        long landCount = silhouette == null
                ? calibration.landCount()
                : Math.min(calibration.landCount(), silhouette.supportCellCount());
        if (landCount <= 0L) {
            return new V12LandRankPlan(
                    frame,
                    new LegacyV15Random(seed),
                    calibration,
                    recipe,
                    silhouette,
                    LegacyV12Noise.SAMPLE_MAX + 1,
                    -1L,
                    0L);
        }

        LegacyV15Random random = new LegacyV15Random(seed);
        long[] histogram = new long[LegacyV12Noise.SAMPLE_MAX + 1];
        for (long y = 0L; y < domain.height(); y++) {
            long legacyY = frame.legacyY(y);
            for (long x = 0L; x < domain.width(); x++) {
                int potential = potentialAt(
                        random,
                        calibration,
                        recipe,
                        silhouette,
                        x,
                        y,
                        frame.legacyX(x),
                        legacyY);
                if (potential >= 0) histogram[potential]++;
            }
        }

        int threshold = -1;
        long higher = 0L;
        long selectedAtThreshold = 0L;
        for (int potential = LegacyV12Noise.SAMPLE_MAX; potential >= 0; potential--) {
            long count = histogram[potential];
            if (higher + count >= landCount) {
                threshold = potential;
                selectedAtThreshold = landCount - higher;
                break;
            }
            higher += count;
        }
        if (threshold < 0 || selectedAtThreshold <= 0L) {
            throw new IllegalStateException("unable to resolve V12 land-rank threshold");
        }

        long thresholdLastIndex = -1L;
        long seenAtThreshold = 0L;
        long cellIndex = 0L;
        outer:
        for (long y = 0L; y < domain.height(); y++) {
            long legacyY = frame.legacyY(y);
            for (long x = 0L; x < domain.width(); x++, cellIndex++) {
                int potential = potentialAt(
                        random,
                        calibration,
                        recipe,
                        silhouette,
                        x,
                        y,
                        frame.legacyX(x),
                        legacyY);
                if (potential != threshold) continue;
                seenAtThreshold++;
                if (seenAtThreshold == selectedAtThreshold) {
                    thresholdLastIndex = cellIndex;
                    break outer;
                }
            }
        }
        if (thresholdLastIndex < 0L) {
            throw new IllegalStateException("unable to resolve V12 stable rank tie boundary");
        }

        return new V12LandRankPlan(
                frame,
                random,
                calibration,
                recipe,
                silhouette,
                threshold,
                thresholdLastIndex,
                landCount);
    }

    public long landCount() {
        return landCount;
    }

    public int thresholdPotential() {
        return thresholdPotential;
    }

    public boolean isLand(long x, long y) {
        requireCoordinate(x, y);
        if (landCount == 0L) return false;
        int potential = potentialAt(
                random,
                calibration,
                recipe,
                silhouette,
                x,
                y,
                frame.legacyX(x),
                frame.legacyY(y));
        if (potential < 0) return false;
        if (potential > thresholdPotential) return true;
        if (potential < thresholdPotential) return false;
        return frame.cellIndex(x, y) <= thresholdLastCellIndex;
    }

    public int potentialAt(long x, long y) {
        requireCoordinate(x, y);
        return potentialAt(
                random,
                calibration,
                recipe,
                silhouette,
                x,
                y,
                frame.legacyX(x),
                frame.legacyY(y));
    }

    public long legacyX(long x) {
        return frame.legacyX(x);
    }

    public long legacyY(long y) {
        return frame.legacyY(y);
    }

    private void requireCoordinate(long x, long y) {
        if (!frame.domain().contains(x, y)) {
            throw new IllegalArgumentException("coordinate lies outside the V12 land-rank domain");
        }
    }

    private static int potentialAt(
            LegacyV15Random random,
            V12TerrainCalibration calibration,
            V12TerrainRecipe recipe,
            V14LandmassPlan silhouette,
            long localX,
            long localY,
            long legacyX,
            long legacyY) {
        int coherent = LegacyV12Noise.organicValueNoise(
                random,
                LegacyV12Noise.LANDMASS,
                legacyX,
                legacyY,
                calibration.coherentLandmassScale(),
                recipe);
        int fragmented = LegacyV12Noise.organicValueNoise(
                random,
                LegacyV12Noise.FRAGMENT,
                legacyX,
                legacyY,
                calibration.fragmentedLandmassScale(),
                recipe);
        int potential = (int) (((long) coherent * (LegacyV12Noise.PPM - calibration.fragmentationPpm())
                + (long) fragmented * calibration.fragmentationPpm()) / LegacyV12Noise.PPM);
        if (silhouette == null) return potential;

        int silhouettePpm = silhouette.potentialPpmAt(localX, localY);
        if (silhouettePpm <= 0) return -1;
        int basePpm = LegacyV12Noise.sampleToPpm(potential);
        int influencePpm = V14LandmassPlan.SILHOUETTE_INFLUENCE_PPM;
        int blendedPpm = Math.toIntExact(
                ((long) basePpm * (LegacyV12Noise.PPM - influencePpm)
                        + (long) silhouettePpm * influencePpm)
                        / LegacyV12Noise.PPM);
        return LegacyV12Noise.ppmToSample(blendedPpm);
    }
}
