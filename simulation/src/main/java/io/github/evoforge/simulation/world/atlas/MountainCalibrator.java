package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.genesis.MountainIntent;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.spatial.WorldBounds;

/** Resolves semantic mountain intent into exact cell-space operating values. */
@FunctionalInterface
public interface MountainCalibrator {

    MountainCalibration calibrate(WorldGenesis genesis, MountainRecipe recipe);

    static MountainCalibrator standard() {
        return StandardMountainCalibrator.INSTANCE;
    }
}

final class StandardMountainCalibrator implements MountainCalibrator {
    static final StandardMountainCalibrator INSTANCE = new StandardMountainCalibrator();
    private static final int PPM = NormalizedValue.SCALE;

    private StandardMountainCalibrator() {
    }

    @Override
    public MountainCalibration calibrate(WorldGenesis genesis, MountainRecipe recipe) {
        if (genesis == null) throw new IllegalArgumentException("genesis must not be null");
        if (recipe == null) throw new IllegalArgumentException("mountain recipe must not be null");

        WorldBounds bounds = genesis.spec().bounds();
        if (bounds.minZ() >= 0) {
            throw new IllegalArgumentException("V13 mountain generation expects ocean-capable bounds below z=0");
        }
        if (bounds.maxZ() <= recipe.baseTerrainCeilingCells()) {
            throw new IllegalArgumentException(
                    "V13 mountain generation needs positive headroom above the V12 base-terrain ceiling");
        }

        int width = Math.toIntExact((long) bounds.maxX() - bounds.minX() + 1L);
        int height = Math.toIntExact((long) bounds.maxY() - bounds.minY() + 1L);
        int area = Math.toIntExact(Math.multiplyExact((long) width, height));
        MountainIntent intent = genesis.generationIntent().mountains();

        long baseCeiling = Math.multiplyExact(
                (long) recipe.baseTerrainCeilingCells(), ElevationField.SUBUNITS_PER_CELL);
        long mountainCeiling = Math.multiplyExact(
                (long) bounds.maxZ(), ElevationField.SUBUNITS_PER_CELL);
        long availableHeadroom = mountainCeiling - baseCeiling;

        int heightPpm = interpolate(
                recipe.minimumHeightHeadroomPpm(),
                recipe.maximumHeightHeadroomPpm(),
                intent.height().partsPerMillion());
        long typicalUplift = availableHeadroom * heightPpm / PPM;

        int authoredHalfWidth = interpolate(
                recipe.minimumHalfWidthCells(),
                recipe.maximumHalfWidthCells(),
                intent.scale().partsPerMillion());

        int allowedRisePpm = interpolate(
                recipe.minimumAllowedRisePpm(),
                recipe.maximumAllowedRisePpm(),
                intent.peakSharpness().partsPerMillion());
        long allowedRise = Math.max(
                1L,
                ElevationField.SUBUNITS_PER_CELL * (long) allowedRisePpm / PPM);

        // A smoothstep hill can have a steeper middle band than simple height / radius suggests.
        // Reserve 1.5x horizontal room so ordinary settings predominantly compile to coherent ramps
        // instead of full-cell walls.
        int coupledHalfWidth = typicalUplift == 0L
                ? recipe.minimumHalfWidthCells()
                : Math.toIntExact(Math.max(
                        1L,
                        (typicalUplift * 3L + allowedRise * 2L - 1L) / (allowedRise * 2L)));
        int typicalHalfWidth = Math.max(authoredHalfWidth, coupledHalfWidth);

        int chaininessPpm = intent.chaininess().partsPerMillion();
        int longAxisWidthPpm = interpolate(
                recipe.minimumLongAxisWidthPpm(),
                recipe.maximumLongAxisWidthPpm(),
                chaininessPpm);
        int typicalLongAxis = Math.max(
                typicalHalfWidth,
                Math.toIntExact((long) typicalHalfWidth * longAxisWidthPpm / PPM));

        int baseSpacing = Math.max(
                1,
                typicalHalfWidth * recipe.candidateSpacingNumerator()
                        / recipe.candidateSpacingDenominator());
        int candidateSpacing = Math.max(baseSpacing, typicalLongAxis * 6 / 5);

        int sharpnessMilli = interpolate(
                recipe.minimumSharpnessMilli(),
                recipe.maximumSharpnessMilli(),
                intent.peakSharpness().partsPerMillion());
        boolean plateausEnabled = intent.plateausEnabled();
        int plateauProbability = plateausEnabled
                ? intent.plateauProbability().partsPerMillion()
                : 0;

        return new MountainCalibration(
                width,
                height,
                area,
                candidateSpacing,
                intent.abundance().partsPerMillion(),
                typicalHalfWidth,
                typicalLongAxis,
                typicalUplift,
                intent.peakSharpness().partsPerMillion(),
                sharpnessMilli,
                plateausEnabled,
                plateauProbability,
                baseCeiling,
                mountainCeiling);
    }

    private static int interpolate(int minimum, int maximum, int coordinatePpm) {
        return minimum + (int) ((long) (maximum - minimum) * coordinatePpm / PPM);
    }
}
