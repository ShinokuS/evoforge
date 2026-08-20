package io.github.evoforge.simulation.world.atlas.hydrology;

import io.github.evoforge.simulation.world.atlas.ElevationField;

/**
 * Composition-only Stage 2B owner for standing-water analysis, terrain depression topology and
 * inland-lake formation.
 *
 * <p>Existing sea-level standing-water graph logic is deliberately preserved as one dependency.
 * Above-sea lakes remain a distinct fact until downstream routing is upgraded to consume explicit
 * per-water-body surface elevation rather than assuming every water body starts at Z=0.
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

    public WorldHydrologyTopology generate(ElevationField elevation) {
        if (elevation == null) throw new IllegalArgumentException("elevation must not be null");

        StandingWaterHydrologyTopology standingWater = require(
                standingWaterStage.generate(elevation),
                "standing-water topology stage");
        DrainageBasinTopology basins = require(
                basinAnalyzer.analyze(elevation, standingWater.standingWater()),
                "drainage basin analyzer");
        InlandLakeTopology lakes = require(
                lakeFormationAlgorithm.generate(elevation, basins, lakeRecipe),
                "inland lake formation algorithm");

        return new WorldHydrologyTopology(standingWater, basins, lakes);
    }

    private static <T> T require(T value, String owner) {
        if (value == null) throw new IllegalStateException(owner + " returned null");
        return value;
    }
}
