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
 * <p>The immutable 65,536-bin potential distribution is retained as compact generation metadata.
 * V15 lake compensation changes only the requested land count, not the potential field itself, so an
 * authoritative rank can reuse this distribution exactly. On small oracle domains the existing
 * bounded potential cache also makes the stable tie lookup a cheap integer scan; on larger domains
 * reranking needs at most one row-streaming tie pass rather than another histogram pass.</p>
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
    private final PotentialDistribution distribution;

    private V12LandRankPlan(
            V15TerrainCoordinateFrame frame,
            LegacyV15Random random,
            V12TerrainCalibration calibration,
            V12TerrainRecipe recipe,
            V14LandmassPlan silhouette,
            int thresholdPotential,
            long thresholdLastCellIndex,
            long landCount,
            PotentialDistribution distribution) {
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
                distribution);
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
            PotentialDistribution distribution) {
        this.frame = frame;
        this.random = random;
        this.calibration = calibration;
        this.recipe = recipe;
        this.silhouette = silhouette;
        this.thresholdPotential = thresholdPotential;
        this.thresholdLastCellIndex = thresholdLastCellIndex;
        this.landCount = landCount;
        this.exclusion = exclusion;
        this.distribution = distribution;
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
        requireInputs(domain, calibration, recipe);
        V15TerrainCoordinateFrame frame = V15TerrainCoordinateFrame.centered(domain);
        long landCount = resolvedLandCount(calibration, silhouette);
        LegacyV15Random random = new LegacyV15Random(seed);
        if (landCount <= 0L) {
            return emptyPlan(frame, random, calibration, recipe, silhouette, null);
        }

        PotentialDistribution distribution = buildDistribution(
                domain, frame, random, calibration, recipe, silhouette);
        return resolveRank(
                frame,
                random,
                calibration,
                recipe,
                silhouette,
                distribution,
                landCount);
    }

    /**
     * Re-resolves only the global rank target over the exact same immutable V12/V14 potential field.
     * This is valid when a caller changes land coverage while preserving every input that authors the
     * potential value itself, as V15 lake compensation does.
     */
    V12LandRankPlan rerank(V12TerrainCalibration newCalibration) {
        if (newCalibration == null) {
            throw new IllegalArgumentException("rerank calibration must not be null");
        }
        if (exclusion != null) {
            throw new IllegalStateException("cannot rerank an already-excluded land plan");
        }
        requireSamePotentialField(calibration, newCalibration);
        long newLandCount = resolvedLandCount(newCalibration, silhouette);
        if (newLandCount <= 0L) {
            return emptyPlan(frame, random, newCalibration, recipe, silhouette, distribution);
        }
        if (distribution == null) {
            throw new IllegalStateException("V12 potential distribution is unavailable for rerank");
        }
        return resolveRank(
                frame,
                random,
                newCalibration,
                recipe,
                silhouette,
                distribution,
                newLandCount);
    }

    private static PotentialDistribution buildDistribution(
            ContinuumWorldDomain domain,
            V15TerrainCoordinateFrame frame,
            LegacyV15Random random,
            V12TerrainCalibration calibration,
            V12TerrainRecipe recipe,
            V14LandmassPlan silhouette) {
        int width = Math.toIntExact(domain.width());
        int height = Math.toIntExact(domain.height());
        int[] boundedPotential = boundedPotentialCache(domain);
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
        return new PotentialDistribution(histogram, boundedPotential);
    }

    private static V12LandRankPlan resolveRank(
            V15TerrainCoordinateFrame frame,
            LegacyV15Random random,
            V12TerrainCalibration calibration,
            V12TerrainRecipe recipe,
            V14LandmassPlan silhouette,
            PotentialDistribution distribution,
            long landCount) {
        ThresholdSelection selection = selectThreshold(distribution.histogram(), landCount);
        long thresholdLastIndex = resolveTieBoundary(
                frame,
                random,
                calibration,
                recipe,
                silhouette,
                distribution,
                selection.threshold(),
                selection.selectedAtThreshold());
        return new V12LandRankPlan(
                frame,
                random,
                calibration,
                recipe,
                silhouette,
                selection.threshold(),
                thresholdLastIndex,
                landCount,
                distribution);
    }

    private static ThresholdSelection selectThreshold(long[] histogram, long landCount) {
        long higher = 0L;
        for (int potential = LegacyV12Noise.SAMPLE_MAX; potential >= 0; potential--) {
            long count = histogram[potential];
            if (higher + count >= landCount) {
                long selectedAtThreshold = landCount - higher;
                if (selectedAtThreshold <= 0L) break;
                return new ThresholdSelection(potential, selectedAtThreshold);
            }
            higher += count;
        }
        throw new IllegalStateException("unable to resolve V12 land-rank threshold");
    }

    private static long resolveTieBoundary(
            V15TerrainCoordinateFrame frame,
            LegacyV15Random random,
            V12TerrainCalibration calibration,
            V12TerrainRecipe recipe,
            V14LandmassPlan silhouette,
            PotentialDistribution distribution,
            int threshold,
            long selectedAtThreshold) {
        int[] boundedPotential = distribution.boundedPotential();
        if (boundedPotential != null) {
            long seen = 0L;
            for (int cell = 0; cell < boundedPotential.length; cell++) {
                if (boundedPotential[cell] != threshold) continue;
                seen++;
                if (seen == selectedAtThreshold) return cell;
            }
            throw new IllegalStateException("unable to resolve bounded V12 stable rank tie boundary");
        }

        int width = calibration.width();
        int height = calibration.height();
        int[] silhouettePotentialRow = silhouette == null ? null : new int[width];
        V14LandmassPlan.PotentialRowCursor silhouetteRows =
                silhouette == null ? null : silhouette.potentialRowCursor();
        long seen = 0L;
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
                if (potential != threshold) continue;
                seen++;
                if (seen == selectedAtThreshold) return cellIndex;
            }
        }
        throw new IllegalStateException("unable to resolve V12 stable rank tie boundary");
    }

    private static V12LandRankPlan emptyPlan(
            V15TerrainCoordinateFrame frame,
            LegacyV15Random random,
            V12TerrainCalibration calibration,
            V12TerrainRecipe recipe,
            V14LandmassPlan silhouette,
            PotentialDistribution distribution) {
        return new V12LandRankPlan(
                frame,
                random,
                calibration,
                recipe,
                silhouette,
                LegacyV12Noise.SAMPLE_MAX + 1,
                -1L,
                0L,
                distribution);
    }

    private static long resolvedLandCount(
            V12TerrainCalibration calibration,
            V14LandmassPlan silhouette) {
        return silhouette == null
                ? calibration.landCount()
                : Math.min(calibration.landCount(), silhouette.supportCellCount());
    }

    private static void requireInputs(
            ContinuumWorldDomain domain,
            V12TerrainCalibration calibration,
            V12TerrainRecipe recipe) {
        if (domain == null || calibration == null || recipe == null) {
            throw new IllegalArgumentException("V12 land-rank inputs must not be null");
        }
        if (domain.width() != calibration.width() || domain.height() != calibration.height()) {
            throw new IllegalArgumentException("V12 calibration must match its Continuum domain");
        }
    }

    private static void requireSamePotentialField(
            V12TerrainCalibration first,
            V12TerrainCalibration second) {
        if (first.width() != second.width()
                || first.height() != second.height()
                || first.coherentLandmassScale() != second.coherentLandmassScale()
                || first.fragmentedLandmassScale() != second.fragmentedLandmassScale()
                || first.fragmentationPpm() != second.fragmentationPpm()) {
            throw new IllegalArgumentException(
                    "rerank calibration changes V12 potential authorship, not only land count");
        }
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
                distribution);
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
        return selectedAt(x, y, potential);
    }

    /**
     * Fills an exact unit-resolution membership window. Full-width streaming requests reuse the V14
     * relaxed-row cursor so each coastline row is evaluated once instead of rebuilding the point
     * relaxation stencil per cell. Arbitrary local windows retain the point-exact fallback.
     */
    public void fillLandWindow(long minX, long minY, int width, int height, boolean[] target) {
        if (width <= 0 || height <= 0 || target == null
                || target.length < Math.multiplyExact(width, height)) {
            throw new IllegalArgumentException("V12 land window dimensions/output are invalid");
        }
        long maxX = Math.addExact(minX, width - 1L);
        long maxY = Math.addExact(minY, height - 1L);
        if (!frame.domain().contains(minX, minY) || !frame.domain().contains(maxX, maxY)) {
            throw new IllegalArgumentException("V12 land window lies outside the rank domain");
        }
        if (landCount == 0L) {
            java.util.Arrays.fill(target, 0, Math.multiplyExact(width, height), false);
            return;
        }

        int[] boundedPotential = distribution == null ? null : distribution.boundedPotential();
        if (boundedPotential != null) {
            int cursor = 0;
            for (int localY = 0; localY < height; localY++) {
                long y = minY + localY;
                for (int localX = 0; localX < width; localX++, cursor++) {
                    long x = minX + localX;
                    int potential = boundedPotential[Math.toIntExact(frame.cellIndex(x, y))];
                    target[cursor] = selectedAt(x, y, potential);
                }
            }
            return;
        }

        if (silhouette != null && minX == 0L && width == calibration.width()) {
            int[] silhouettePotentialRow = new int[width];
            V14LandmassPlan.PotentialRowCursor silhouetteRows = silhouette.potentialRowCursor();
            int cursor = 0;
            for (int localY = 0; localY < height; localY++) {
                int y = Math.toIntExact(minY + localY);
                long legacyY = frame.legacyY(y);
                silhouetteRows.fill(y, silhouettePotentialRow);
                for (int x = 0; x < width; x++, cursor++) {
                    int potential = basePotentialAt(
                            random,
                            calibration,
                            recipe,
                            frame.legacyX(x),
                            legacyY);
                    potential = blendWithSilhouette(potential, silhouettePotentialRow[x]);
                    target[cursor] = selectedAt(x, y, potential);
                }
            }
            return;
        }

        int cursor = 0;
        for (int localY = 0; localY < height; localY++) {
            long y = minY + localY;
            for (int localX = 0; localX < width; localX++, cursor++) {
                long x = minX + localX;
                target[cursor] = isLand(x, y);
            }
        }
    }

    private boolean selectedAt(long x, long y, int potential) {
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
        int[] boundedPotential = distribution == null ? null : distribution.boundedPotential();
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

    private record PotentialDistribution(long[] histogram, int[] boundedPotential) {}
    private record ThresholdSelection(int threshold, long selectedAtThreshold) {}
}
