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
        if (source == null || policy == null) {
            throw new IllegalArgumentException("coast relaxation inputs must not be null");
        }
        if (width <= 0 || height <= 0 || source.length != Math.multiplyExact(width, height)) {
            throw new IllegalArgumentException("coast relaxation dimensions must match source field");
        }
        if (lockedOceanEdgeCells < 0 || plateSpacingCells <= 0) {
            throw new IllegalArgumentException("coast relaxation geometry must be positive");
        }

        double bandWidth = Math.max(
                1.5d,
                plateSpacingCells * policy.bandWidthSpacingPpm() / (double) PPM);
        double maximumShift = policy.maximumShiftPpmOfCell() / (double) PPM;
        double[] current = source.clone();

        for (int pass = 0; pass < policy.passes(); pass++) {
            double[] next = current.clone();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (edgeDistance(x, y, width, height) < lockedOceanEdgeCells) {
                        continue;
                    }
                    int index = y * width + x;
                    double center = current[index];
                    if (!Double.isFinite(center) || StrictMath.abs(center) > bandWidth) {
                        continue;
                    }

                    double weighted = center * policy.selfWeightPpm();
                    long totalWeight = policy.selfWeightPpm();
                    for (int oy = -1; oy <= 1; oy++) {
                        for (int ox = -1; ox <= 1; ox++) {
                            if (ox == 0 && oy == 0) continue;
                            int nx = x + ox;
                            int ny = y + oy;
                            if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue;
                            double neighbor = current[ny * width + nx];
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
                    next[index] = center + shift;
                }
            }
            current = next;
        }
        return current;
    }

    private static int edgeDistance(int x, int y, int width, int height) {
        return Math.min(Math.min(x, width - 1 - x), Math.min(y, height - 1 - y));
    }
}
