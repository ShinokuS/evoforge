package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.spatial.WorldBounds;

/** Resolves world scale into exact operating values for ocean-bounded landmass selection. */
@FunctionalInterface
public interface LandmassBoundaryCalibrator {
    LandmassBoundaryCalibration calibrate(WorldGenesis genesis, LandmassBoundaryRecipe recipe);

    static LandmassBoundaryCalibrator standard() {
        return StandardLandmassBoundaryCalibrator.INSTANCE;
    }
}

final class StandardLandmassBoundaryCalibrator implements LandmassBoundaryCalibrator {
    static final StandardLandmassBoundaryCalibrator INSTANCE = new StandardLandmassBoundaryCalibrator();
    private static final double MILLI = 1_000d;

    private StandardLandmassBoundaryCalibrator() {
    }

    @Override
    public LandmassBoundaryCalibration calibrate(
            WorldGenesis genesis,
            LandmassBoundaryRecipe recipe) {
        if (genesis == null) throw new IllegalArgumentException("genesis must not be null");
        if (recipe == null) throw new IllegalArgumentException("landmass boundary recipe must not be null");

        WorldBounds bounds = genesis.spec().bounds();
        int width = Math.toIntExact((long) bounds.maxX() - bounds.minX() + 1L);
        int height = Math.toIntExact((long) bounds.maxY() - bounds.minY() + 1L);
        int limitingSpan = Math.min(width, height);
        if (limitingSpan < 5) {
            throw new IllegalArgumentException("ocean-bounded landmass generation needs at least a 5-cell span");
        }

        int maximumMargin = Math.max(1, (limitingSpan - 4) / 2);
        int scaledMargin = ceilFinite(
                (recipe.marginRootScaleMilli() / MILLI) * StrictMath.sqrt(limitingSpan),
                "ocean margin");
        int margin = Math.min(
                maximumMargin,
                Math.max(recipe.minimumOceanMarginCells(), scaledMargin));

        int remainingSpan = limitingSpan - margin * 2;
        int maximumTransition = Math.max(1, remainingSpan / 2);
        int transition = Math.min(
                maximumTransition,
                Math.max(1, ceilScaled(margin, recipe.transitionMarginScaleMilli())));
        int variation = Math.min(
                transition,
                ceilScaled(transition, recipe.edgeVariationTransitionScaleMilli()));
        int noiseScale = Math.max(
                4,
                ceilScaled(margin, recipe.edgeNoiseMarginScaleMilli()));

        int candidateWidth = Math.max(0, width - margin * 2);
        int candidateHeight = Math.max(0, height - margin * 2);
        int maximumLandCells = Math.multiplyExact(candidateWidth, candidateHeight);
        return new LandmassBoundaryCalibration(
                margin,
                transition,
                variation,
                noiseScale,
                maximumLandCells);
    }

    private static int ceilScaled(int value, int scaleMilli) {
        return Math.toIntExact(((long) value * scaleMilli + 999L) / 1_000L);
    }

    private static int ceilFinite(double value, String label) {
        if (!Double.isFinite(value) || value <= 0d || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(label + " must be finite and positive");
        }
        return (int) StrictMath.ceil(value);
    }
}
