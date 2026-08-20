package io.github.evoforge.simulation.world.atlas.hydrology;

import io.github.evoforge.simulation.world.atlas.ElevationField;

/**
 * Composition-only Stage 2B analysis over final authoritative terrain.
 *
 * <p>This stage never authors or repairs elevation. It classifies standing water, derives closed
 * depression topology from the supplied terrain and lets an independent lake owner decide which
 * basins contain standing inland water.</p>
 */
public final class WorldHydrologyTopologyStage {
    private final StandingWaterHydrologyTopologyStage standingWaterStage;
    private final DrainageBasinTopologyAnalyzer basinAnalyzer;
    private final InlandLakeFormationAlgorithm lakeFormationAlgorithm;
    private final InlandLakeFormationRecipe lakeRecipe;

    public WorldHydrologyTopologyStage(
            StandingWaterHydrologyTopologyStage standingWaterStage,
            DrainageBasinTopologyAnalyzer basinAnalyzer,
            InlandLakeFormationAlgorithm lakeFormationAlgorithm,
            InlandLakeFormationRecipe lakeRecipe) {
        if (standingWaterStage == null
                || basinAnalyzer == null
                || lakeFormationAlgorithm == null
                || lakeRecipe == null) {
            throw new IllegalArgumentException("world hydrology stage dependencies must not be null");
        }
        this.standingWaterStage = standingWaterStage;
        this.basinAnalyzer = basinAnalyzer;
        this.lakeFormationAlgorithm = lakeFormationAlgorithm;
        this.lakeRecipe = lakeRecipe;
    }

    public static WorldHydrologyTopologyStage standard() {
        return new WorldHydrologyTopologyStage(
                StandingWaterHydrologyTopologyStage.standard(),
                DrainageBasinTopologyAnalyzer.standard(),
                InlandLakeFormationAlgorithm.standard(),
                InlandLakeFormationRecipe.balanced());
    }

    public WorldHydrologyTopology generate(ElevationField finalElevation) {
        if (finalElevation == null) throw new IllegalArgumentException("elevation must not be null");

        StandingWaterHydrologyTopology standingWater = require(
                standingWaterStage.generate(finalElevation),
                "standing-water topology stage");
        DrainageBasinTopology basins = require(
                basinAnalyzer.analyze(finalElevation, standingWater.standingWater()),
                "drainage basin analyzer");
        InlandLakeTopology lakes = require(
                lakeFormationAlgorithm.generate(finalElevation, basins, lakeRecipe),
                "inland lake formation algorithm");
        return new WorldHydrologyTopology(standingWater, basins, lakes);
    }

    private static <T> T require(T value, String owner) {
        if (value == null) throw new IllegalStateException(owner + " returned null");
        return value;
    }
}
