package io.github.evoforge.simulation.world.atlas.hydrology;

import io.github.evoforge.simulation.world.atlas.ElevationField;

/** Composition-only Stage 2B.1 analysis over accepted V14 elevation. */
public final class StandingWaterHydrologyTopologyStage {
    private final StandingWaterTopologyAnalyzer waterAnalyzer;
    private final StandingWaterBodySelector bodySelector;
    private final StandingWaterMorphologyAnalyzer morphologyAnalyzer;
    private final StandingWaterExternalSinkCalibrator externalSinkCalibrator;
    private final StandingWaterExternalSinkRecipe externalSinkRecipe;
    private final StandingWaterExternalSinkResolver externalSinkResolver;
    private final StandingWaterRimTopologyAnalyzer rimAnalyzer;
    private final StandingWaterSpillTopologyAnalyzer spillAnalyzer;
    private final StandingWaterBoundaryRouteResolver routeResolver;

    public StandingWaterHydrologyTopologyStage(
            StandingWaterTopologyAnalyzer waterAnalyzer,
            StandingWaterBodySelector bodySelector,
            StandingWaterMorphologyAnalyzer morphologyAnalyzer,
            StandingWaterExternalSinkCalibrator externalSinkCalibrator,
            StandingWaterExternalSinkRecipe externalSinkRecipe,
            StandingWaterExternalSinkResolver externalSinkResolver,
            StandingWaterRimTopologyAnalyzer rimAnalyzer,
            StandingWaterSpillTopologyAnalyzer spillAnalyzer,
            StandingWaterBoundaryRouteResolver routeResolver) {
        if (waterAnalyzer == null
                || bodySelector == null
                || morphologyAnalyzer == null
                || externalSinkCalibrator == null
                || externalSinkRecipe == null
                || externalSinkResolver == null
                || rimAnalyzer == null
                || spillAnalyzer == null
                || routeResolver == null) {
            throw new IllegalArgumentException("standing-water topology stage dependencies must not be null");
        }
        this.waterAnalyzer = waterAnalyzer;
        this.bodySelector = bodySelector;
        this.morphologyAnalyzer = morphologyAnalyzer;
        this.externalSinkCalibrator = externalSinkCalibrator;
        this.externalSinkRecipe = externalSinkRecipe;
        this.externalSinkResolver = externalSinkResolver;
        this.rimAnalyzer = rimAnalyzer;
        this.spillAnalyzer = spillAnalyzer;
        this.routeResolver = routeResolver;
    }

    public static StandingWaterHydrologyTopologyStage standard() {
        return new StandingWaterHydrologyTopologyStage(
                new ConnectedStandingWaterTopologyAnalyzer(),
                new BroadStandingWaterBodySelector(),
                StandingWaterMorphologyAnalyzer.standard(),
                StandingWaterExternalSinkCalibrator.standard(),
                StandingWaterExternalSinkRecipe.balanced(),
                StandingWaterExternalSinkResolver.standard(),
                new CardinalStandingWaterRimTopologyAnalyzer(),
                new PriorityFloodStandingWaterSpillTopologyAnalyzer(),
                new MinimaxStandingWaterBoundaryRouteResolver());
    }

    public StandingWaterHydrologyTopology generate(ElevationField elevation) {
        if (elevation == null) throw new IllegalArgumentException("elevation must not be null");
        StandingWaterTopology rawWater = require(
                waterAnalyzer.analyze(elevation),
                "standing-water analyzer");
        StandingWaterTopology water = require(
                bodySelector.select(rawWater),
                "standing-water body selector");
        if (!rawWater.bounds().equals(water.bounds())) {
            throw new IllegalStateException("standing-water body selector changed world bounds");
        }
        StandingWaterMorphologyTopology morphology = require(
                morphologyAnalyzer.analyze(water),
                "standing-water morphology analyzer");
        StandingWaterExternalSinkCalibration sinkCalibration = require(
                externalSinkCalibrator.calibrate(water.bounds(), externalSinkRecipe),
                "standing-water external-sink calibrator");
        StandingWaterExternalSinkTopology externalSinks = require(
                externalSinkResolver.resolve(water, morphology, sinkCalibration),
                "standing-water external-sink resolver");
        StandingWaterRimTopology rims = require(
                rimAnalyzer.analyze(elevation, water),
                "standing-water rim analyzer");
        StandingWaterSpillTopology spills = require(
                spillAnalyzer.analyze(elevation, water),
                "standing-water spill analyzer");
        StandingWaterBoundaryRouteTopology routes = require(
                routeResolver.resolve(water, spills, externalSinks),
                "standing-water external route resolver");
        return new StandingWaterHydrologyTopology(
                rawWater,
                water,
                morphology,
                externalSinks,
                rims,
                spills,
                routes);
    }

    private static <T> T require(T value, String owner) {
        if (value == null) throw new IllegalStateException(owner + " returned null");
        return value;
    }
}
