package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.definition.NormalizedValue;

/**
 * Relaxes only the near-zero signed coast field before land/water thresholding.
 *
 * <p>This operates on the continuous coastline representation rather than on a binary land mask.
 * Deep land and deep ocean remain unchanged, the guaranteed external-ocean edge is locked, and
 * each pass has a strict sub-cell displacement bound so topology remains owned by the plate
 * scaffold rather than by smoothing.</p>
 */
@FunctionalInterface
interface CoastFieldRelaxationAlgorithm {
    double[] relax(
            double[] source,
            int width,
            int height,
            int lockedOceanEdgeCells,
            int plateSpacingCells,
            LandmassSilhouetteRecipe.CoastRelaxationPolicy policy);

    /**
     * Relaxes a freshly allocated field whose storage ownership is transferred to this algorithm.
     *
     * <p>The default remains copy-safe for alternate implementations. The standard implementation
     * overrides this method so callers with exclusive ownership may avoid the initial full-field
     * copy while retaining identical relaxation arithmetic.</p>
     */
    default double[] relaxOwned(
            double[] ownedSource,
            int width,
            int height,
            int lockedOceanEdgeCells,
            int plateSpacingCells,
            LandmassSilhouetteRecipe.CoastRelaxationPolicy policy) {
        return relax(
                ownedSource,
                width,
                height,
                lockedOceanEdgeCells,
                plateSpacingCells,
                policy);
    }

    static CoastFieldRelaxationAlgorithm standard() {
        return WeightedCoastFieldRelaxationAlgorithm.INSTANCE;
    }
}

final class WeightedCoastFieldRelaxationAlgorithm implements CoastFieldRelaxationAlgorithm {
    static final WeightedCoastFieldRelaxationAlgorithm INSTANCE = new WeightedCoastFieldRelaxationAlgorithm();
    private static final int PPM = NormalizedValue.SCALE;

    private WeightedCoastFieldRelaxationAlgorithm() {
    }

    @Override
    public double[] relax(
            double[] source,
            int width,
            int height,
            int lockedOceanEdgeCells,
            int plateSpacingCells,
            LandmassSilhouetteRecipe.CoastRelaxationPolicy policy) {
        requireInputs(source, width, height, lockedOceanEdgeCells, plateSpacingCells, policy);
        return relaxOwned(
                source.clone(),
                width,
                height,
                lockedOceanEdgeCells,
                plateSpacingCells,
                policy);
    }

    @Override
    public double[] relaxOwned(
            double[] ownedSource,
            int width,
            int height,
            int lockedOceanEdgeCells,
            int plateSpacingCells,
            LandmassSilhouetteRecipe.CoastRelaxationPolicy policy) {
        requireInputs(ownedSource, width, height, lockedOceanEdgeCells, plateSpacingCells, policy);
        if (policy.passes() == 0) return ownedSource;

        double bandWidth = Math.max(
                1.5d,
                plateSpacingCells * policy.bandWidthSpacingPpm() / (double) PPM);
        double maximumShift = policy.maximumShiftPpmOfCell() / (double) PPM;
        double[] previousRow = new double[width];
        double[] centerRow = new double[width];

        for (int pass = 0; pass < policy.passes(); pass++) {
            relaxPassInPlace(
                    ownedSource,
                    width,
                    height,
                    lockedOceanEdgeCells,
                    bandWidth,
                    maximumShift,
                    policy,
                    previousRow,
                    centerRow);
        }
        return ownedSource;
    }

    /**
     * Applies one mathematically out-of-place 3x3 pass while overwriting the field row by row.
     *
     * <p>Only rows already overwritten need preservation: the old previous row and the old current
     * row. The next row still lives untouched in {@code field}. Reading neighbors through these
     * three sources therefore observes exactly the same previous-pass values as the former
     * full-field clone, while auxiliary memory is O(width).</p>
     */
    private static void relaxPassInPlace(
            double[] field,
            int width,
            int height,
            int lockedOceanEdgeCells,
            double bandWidth,
            double maximumShift,
            LandmassSilhouetteRecipe.CoastRelaxationPolicy policy,
            double[] previousRow,
            double[] centerRow) {
        if (height == 0) return;
        System.arraycopy(field, 0, centerRow, 0, width);

        for (int y = 0; y < height; y++) {
            int rowStart = y * width;
            for (int x = 0; x < width; x++) {
                if (edgeDistance(x, y, width, height) < lockedOceanEdgeCells) continue;

                double center = centerRow[x];
                if (!Double.isFinite(center) || StrictMath.abs(center) > bandWidth) continue;

                double weighted = center * policy.selfWeightPpm();
                long totalWeight = policy.selfWeightPpm();
                for (int oy = -1; oy <= 1; oy++) {
                    int ny = y + oy;
                    if (ny < 0 || ny >= height) continue;
                    for (int ox = -1; ox <= 1; ox++) {
                        if (ox == 0 && oy == 0) continue;
                        int nx = x + ox;
                        if (nx < 0 || nx >= width) continue;

                        double neighbor;
                        if (oy < 0) {
                            neighbor = previousRow[nx];
                        } else if (oy == 0) {
                            neighbor = centerRow[nx];
                        } else {
                            neighbor = field[(y + 1) * width + nx];
                        }
                        if (!Double.isFinite(neighbor)) continue;
                        int weight = ox == 0 || oy == 0
                                ? policy.orthogonalNeighborWeightPpm()
                                : policy.diagonalNeighborWeightPpm();
                        weighted += neighbor * weight;
                        totalWeight += weight;
                    }
                }

                double target = weighted / totalWeight;
                double shift = Math.max(-maximumShift, Math.min(maximumShift, target - center));
                field[rowStart + x] = center + shift;
            }

            double[] swap = previousRow;
            previousRow = centerRow;
            centerRow = swap;
            if (y + 1 < height) {
                System.arraycopy(field, (y + 1) * width, centerRow, 0, width);
            }
        }
    }

    private static void requireInputs(
            double[] source,
            int width,
            int height,
            int lockedOceanEdgeCells,
            int plateSpacingCells,
            LandmassSilhouetteRecipe.CoastRelaxationPolicy policy) {
        if (source == null || policy == null) {
            throw new IllegalArgumentException("coast relaxation inputs must not be null");
        }
        if (width <= 0 || height <= 0 || source.length != Math.multiplyExact(width, height)) {
            throw new IllegalArgumentException("coast relaxation dimensions must match source field");
        }
        if (lockedOceanEdgeCells < 0 || plateSpacingCells <= 0) {
            throw new IllegalArgumentException("coast relaxation geometry must be positive");
        }
    }

    private static int edgeDistance(int x, int y, int width, int height) {
        return Math.min(Math.min(x, width - 1 - x), Math.min(y, height - 1 - y));
    }
}
