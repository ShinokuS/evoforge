package io.github.evoforge.simulation.world.terrain.genesis;

import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;

/**
 * Exact V12 global land-rank decision without retaining the old {@code long[worldArea]} sort.
 *
 * <p>The accepted V12 potential is only 16-bit. A fixed 65,536-bin histogram therefore reproduces
 * the same descending potential order, while one second streaming pass reproduces the old stable
 * row-major tie break. Optional V14 constraint uses the exact old 900k silhouette blend.</p>
 *
 * <p>When V14 constrains the rank, the mandatory global passes consume its relaxed silhouette one
 * row at a time through the same bounded V14 row cache used by coastline preparation. This preserves
 * the exact per-cell potential while avoiding a recursive coast-stencil rebuild for every ranked
 * cell. Only one {@code int[width]} silhouette row is retained.</p>
 *
 * <p>For genuinely small finite domains, the first mandatory histogram pass also retains the exact
 * derived potential in a hard-bounded cache. This avoids re-running the expensive V14 coastline
 * evaluation during the stable-rank pass and later V12 slope/materialization queries. The cache is
 * capped at 512 x 512 cells (1 MiB of {@code int}s), cannot scale with the Continuum address space,
 * and is immutable execution metadata rather than authoritative terrain output.</p>
 */
public final class V12LandRankPlan {
    private static final int MAX_BOUNDED_POTENTIAL_CELLS = 512 * 512;

    @FunctionalInterface
    public interface CoordinateExclusion {
        boolean excludes(long x, long y);
    }

    private final V15TerrainCoordinateFrame frame;
    private final LegacyV15Random random;
    private final V12TerrainCalibration calibration;
    private final V12TerrainRecipe recipe;
    private final V14LandmassPlan silhouette;
    private final int thresholdPotential;
    private final long thresholdLastCellIndex;
    private final long landCount;
    private final CoordinateExclusion exclusion;
    private final int[] boundedPotential;

    private V12LandRankPlan(
            V15TerrainCoordinateFrame frame,
            LegacyV15Random random,
            V12TerrainCalibration calibration,
            V12TerrainRecipe recipe,
            V14LandmassPlan silhouette,
            int thresholdPotential,
            long thresholdLastCellIndex,
            long landCount,
            int[] boundedPotential) {
        this(
                frame,
                random,
                calibration,
                recipe,
                silhouette,
                thresholdPotential,
                thresholdLastCellIndex,
                landCount,
                null,
                boundedPotential);
    }

    private V12LandRankPlan(
            V15TerrainCoordinateFrame frame,
            LegacyV15Random random,
            V12TerrainCalibration calibration,
            V12TerrainRecipe recipe,
            V14LandmassPlan silhouette,
            int thresholdPotential,
            long thresholdLastCellIndex,
            long landCount,
            CoordinateExclusion exclusion,
            int[] boundedPotential) {
        this.frame = frame;
        this.random = random;
        this.calibration = calibration;
        this.recipe = recipe;
        this.silhouette = silhouette;
        this.thresholdPotential = thresholdPotential;
        this.thresholdLastCellIndex = thresholdLastCellIndex;
        this.landCount = landCount;
        this.exclusion = exclusion;
        this.boundedPotential = boundedPotential;
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
        int[] boundedPotential = boundedPotentialCache(domain);
        if (landCount <= 0L) {
            return new V12LandRankPlan(
                    frame,
                    new LegacyV15Random(seed),
                    calibration,
                    recipe,
                    silhouette,
                    LegacyV12Noise.SAMPLE_MAX + 1,
                    -1L,
                    0L,
                    boundedPotential);
        }

        LegacyV15Random random = new LegacyV15Random(seed);
        int width = Math.toIntExact(domain.width());
        int height = Math.toIntExact(domain.height());
        int[] silhouettePotentialRow = silhouette == null ? null : new int[width];
        V14LandmassPlan.PotentialRowCursor silhouetteRows =
                silhouette == null ? null : silhouette.potentialRowCursor();

        long[] histogram = new long[LegacyV12Noise.SAMPLE_MAX + 1];
        long cellIndex = 0L;
        for (int y = 0; y < height; y++) {
            long legacyY = frame.legacyY(y);
            if (silhouetteRows != null) silhouetteRows.fill(y, silhouettePotentialRow);
            for (int x = 0; x < width; x++, cellIndex++) {
                int potential = basePotentialAt(
                        random,
                        calibration,
                        recipe,
                        frame.legacyX(x),
                        legacyY);
                if (silhouettePotentialRow != null) {
                    potential = blendWithSilhouette(potential, silhouettePotentialRow[x]);
                }
                if (boundedPotential != null) {
                    boundedPotential[Math.toIntExact(cellIndex)] = potential;
                }
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
        cellIndex = 0L;
        V14LandmassPlan.PotentialRowCursor tieSilhouetteRows =
                boundedPotential == null && silhouette != null ? silhouette.potentialRowCursor() : null;
        outer:
        for (int y = 0; y < height; y++) {
            long legacyY = frame.legacyY(y);
            if (tieSilhouetteRows != null) tieSilhouetteRows.fill(y, silhouettePotentialRow);
            for (int x = 0; x < width; x++, cellIndex++) {
                int potential;
                if (boundedPotential != null) {
                    potential = boundedPotential[Math.toIntExact(cellIndex)];
                } else {
                    potential = basePotentialAt(
                            random,
                            calibration,
                            recipe,
                            frame.legacyX(x),
                            legacyY);
                    if (silhouettePotentialRow != null) {
                        potential = blendWithSilhouette(potential, silhouettePotentialRow[x]);
                    }
                }
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
                landCount,
                boundedPotential);
    }

    /**
     * Returns the same accepted rank decision with an already-proven subset removed from dry land.
     * V15 uses this only after verifying that every excluded lake cell belongs to the compensated
     * continental rank, so V13 sees exactly the historical lake-aware dry-land mask.
     */
    public V12LandRankPlan excluding(long excludedLandCount, CoordinateExclusion excludedCoordinates) {
        if (excludedCoordinates == null) {
            throw new IllegalArgumentException("excludedCoordinates must not be null");
        }
        if (excludedLandCount < 0L || excludedLandCount > landCount) {
            throw new IllegalArgumentException("excludedLandCount must fit the accepted land rank");
        }
        if (exclusion != null) {
            throw new IllegalStateException("land-rank exclusion is already configured");
        }
        return new V12LandRankPlan(
                frame,
                random,
                calibration,
                recipe,
                silhouette,
                thresholdPotential,
                thresholdLastCellIndex,
                landCount - excludedLandCount,
                excludedCoordinates,
                boundedPotential);
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
        int potential = potentialAt(x, y);
        boolean selected;
        if (potential < 0) {
            selected = false;
        } else if (potential > thresholdPotential) {
            selected = true;
        } else if (potential < thresholdPotential) {
            selected = false;
        } else {
            selected = frame.cellIndex(x, y) <= thresholdLastCellIndex;
        }
        return selected && (exclusion == null || !exclusion.excludes(x, y));
    }

    public int potentialAt(long x, long y) {
        requireCoordinate(x, y);
        if (boundedPotential != null) {
            return boundedPotential[Math.toIntExact(frame.cellIndex(x, y))];
        }
        int potential = basePotentialAt(
                random,
                calibration,
                recipe,
                frame.legacyX(x),
                frame.legacyY(y));
        return silhouette == null
                ? potential
                : blendWithSilhouette(potential, silhouette.potentialPpmAt(x, y));
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

    private static int[] boundedPotentialCache(ContinuumWorldDomain domain) {
        long width = domain.width();
        long height = domain.height();
        if (width > MAX_BOUNDED_POTENTIAL_CELLS || height > MAX_BOUNDED_POTENTIAL_CELLS) return null;
        long area = Math.multiplyExact(width, height);
        if (area > MAX_BOUNDED_POTENTIAL_CELLS) return null;
        return new int[Math.toIntExact(area)];
    }

    private static int basePotentialAt(
            LegacyV15Random random,
            V12TerrainCalibration calibration,
            V12TerrainRecipe recipe,
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
        return (int) (((long) coherent * (LegacyV12Noise.PPM - calibration.fragmentationPpm())
                + (long) fragmented * calibration.fragmentationPpm()) / LegacyV12Noise.PPM);
    }

    private static int blendWithSilhouette(int potential, int silhouettePpm) {
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
