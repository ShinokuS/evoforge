package io.github.evoforge.simulation.world.terrain.genesis;

import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.terrain.definition.V13MountainDefinition;
import io.github.evoforge.simulation.world.terrain.field.TerrainElevationField;

/** Exact world-specific V13 mountain operating values compiled without a resident terrain raster. */
public record V13MountainCalibration(
        int width,
        int height,
        int area,
        int candidateSpacingCells,
        int targetCoveragePpm,
        int typicalHalfWidthCells,
        int typicalLongAxisCells,
        long typicalUpliftSubunits,
        long worldHeightCapSubunits,
        long maximumCardinalRiseSubunits,
        int peakSharpnessPpm,
        int sharpnessMilli,
        boolean plateausEnabled,
        int plateauProbabilityPpm,
        int coastalTransitionCells,
        long shorelineUpliftSubunits,
        long baseTerrainCeilingSubunits,
        long mountainCeilingSubunits) {

    private static final int PPM = 1_000_000;

    public static V13MountainCalibration compile(
            ContinuumWorldDomain domain,
            V13MountainDefinition definition,
            V13MountainRecipe recipe,
            int maximumZCells) {
        if (domain == null || definition == null || recipe == null) {
            throw new IllegalArgumentException("V13 mountain calibration inputs must not be null");
        }
        if (maximumZCells <= recipe.baseTerrainCeilingCells()) {
            throw new IllegalArgumentException(
                    "V13 mountain generation needs headroom above the V12 base-terrain ceiling");
        }

        int width = Math.toIntExact(domain.width());
        int height = Math.toIntExact(domain.height());
        int area = Math.toIntExact(Math.multiplyExact(domain.width(), domain.height()));
        int limitingHorizontalSpan = Math.min(width, height);

        long baseCeiling = Math.multiplyExact(
                (long) recipe.baseTerrainCeilingCells(), TerrainElevationField.SUBUNITS_PER_CELL);
        long mountainCeiling = Math.multiplyExact(
                (long) maximumZCells, TerrainElevationField.SUBUNITS_PER_CELL);
        long availableHeadroom = mountainCeiling - baseCeiling;

        int peakSharpnessPpm = definition.peakSharpness().partsPerMillion();
        int allowedRisePpm = interpolate(
                recipe.minimumAllowedRisePpm(),
                recipe.maximumAllowedRisePpm(),
                peakSharpnessPpm);
        long maximumCardinalRise = Math.max(
                1L,
                TerrainElevationField.SUBUNITS_PER_CELL * (long) allowedRisePpm / PPM);

        long usableRadiusCells = Math.max(
                1L,
                (long) limitingHorizontalSpan * recipe.worldSlopeRadiusUtilizationPpm()
                        / (2L * PPM));
        long slopeSupportedHeight = Math.multiplyExact(usableRadiusCells, maximumCardinalRise);
        long worldHeightCap = Math.min(availableHeadroom, slopeSupportedHeight);

        int heightFractionPpm = interpolate(
                recipe.minimumHeightOfWorldSlopeCapPpm(),
                recipe.maximumHeightOfWorldSlopeCapPpm(),
                definition.height().partsPerMillion());
        long desiredUplift = worldHeightCap * heightFractionPpm / PPM;

        int worldMinimumHalfWidth = clamp(
                Math.toIntExact(Math.max(
                        1L,
                        (long) limitingHorizontalSpan * recipe.minimumHalfWidthWorldPpm() / PPM)),
                recipe.absoluteMinimumHalfWidthCells(),
                recipe.absoluteMaximumHalfWidthCells());
        int worldMaximumHalfWidth = clamp(
                Math.toIntExact(Math.max(
                        1L,
                        (long) limitingHorizontalSpan * recipe.maximumHalfWidthWorldPpm() / PPM)),
                worldMinimumHalfWidth,
                recipe.absoluteMaximumHalfWidthCells());
        int authoredHalfWidth = interpolate(
                worldMinimumHalfWidth,
                worldMaximumHalfWidth,
                definition.scale().partsPerMillion());

        int maximumHeightCoupledWidth = Math.min(
                recipe.absoluteMaximumHalfWidthCells(),
                Math.toIntExact(Math.max(
                        authoredHalfWidth,
                        Math.round(authoredHalfWidth
                                * (1.0 + recipe.slopeWidthCouplingPpm() / (double) PPM)))));
        double profileGradientBound = recipe.profileGradientBound(false);
        int widthNeededForDesiredHeight = desiredUplift == 0L
                ? authoredHalfWidth
                : Math.toIntExact(Math.max(
                        1L,
                        (long) StrictMath.ceil(
                                desiredUplift * profileGradientBound
                                        / Math.max(1.0, maximumCardinalRise))));
        int typicalHalfWidth = clamp(
                widthNeededForDesiredHeight,
                authoredHalfWidth,
                maximumHeightCoupledWidth);

        long widthSupportedHeight = Math.max(
                0L,
                (long) StrictMath.floor(
                        typicalHalfWidth * maximumCardinalRise / profileGradientBound));
        long typicalUplift = Math.min(desiredUplift, widthSupportedHeight);

        int longAxisWidthPpm = interpolate(
                recipe.minimumLongAxisWidthPpm(),
                recipe.maximumLongAxisWidthPpm(),
                definition.chaininess().partsPerMillion());
        int typicalLongAxis = Math.max(
                typicalHalfWidth,
                Math.toIntExact((long) typicalHalfWidth * longAxisWidthPpm / PPM));

        int candidateSpacing = Math.max(
                1,
                authoredHalfWidth * recipe.candidateSpacingNumerator()
                        / recipe.candidateSpacingDenominator());
        int targetCoveragePpm = Math.toIntExact(
                (long) definition.abundance().partsPerMillion()
                        * recipe.maximumAbundanceCoveragePpm()
                        / PPM);
        int sharpnessMilli = interpolate(
                recipe.minimumSharpnessMilli(),
                recipe.maximumSharpnessMilli(),
                peakSharpnessPpm);
        boolean plateausEnabled = definition.plateausEnabled();
        int plateauProbability = plateausEnabled
                ? definition.plateauProbability().partsPerMillion()
                : 0;

        long shorelineUplift = Math.min(
                Math.multiplyExact(
                        (long) recipe.maximumShorelineUpliftCells(),
                        TerrainElevationField.SUBUNITS_PER_CELL),
                typicalUplift * recipe.shorelineUpliftPpm() / PPM);
        long coastalRise = Math.max(0L, typicalUplift - shorelineUplift);
        long riseSteps = coastalRise == 0L
                ? 0L
                : (coastalRise + maximumCardinalRise - 1L) / maximumCardinalRise;
        int coastalTransitionCells = Math.max(
                recipe.minimumCoastalTransitionCells(),
                Math.toIntExact(Math.max(0L, (riseSteps * 160L + 99L) / 100L)));

        return new V13MountainCalibration(
                width,
                height,
                area,
                candidateSpacing,
                targetCoveragePpm,
                typicalHalfWidth,
                typicalLongAxis,
                typicalUplift,
                worldHeightCap,
                maximumCardinalRise,
                peakSharpnessPpm,
                sharpnessMilli,
                plateausEnabled,
                plateauProbability,
                coastalTransitionCells,
                shorelineUplift,
                baseCeiling,
                mountainCeiling);
    }

    private static int interpolate(int minimum, int maximum, int coordinatePpm) {
        return minimum + (int) ((long) (maximum - minimum) * coordinatePpm / PPM);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
