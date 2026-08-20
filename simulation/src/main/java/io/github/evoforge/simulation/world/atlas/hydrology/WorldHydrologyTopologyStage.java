package io.github.evoforge.simulation.world.atlas.hydrology;

import io.github.evoforge.simulation.world.atlas.ElevationField;

/**
 * Composition-only Stage 2B owner for lacustrine basin morphology, standing-water analysis,
 * terrain depression topology and inland-lake formation.
 *
 * <p>Existing V14 elevation is immutable input. The basin morphology owner derives a separate
 * hydrologic terrain fact while preserving land/ocean membership exactly. Existing sea-level
 * standing-water graph logic remains one dependency. Above-sea lakes stay distinct until routing
 * consumes explicit per-water-body surface elevation rather than assuming every body starts at Z=0.
 */
public final class WorldHydrologyTopologyStage {
    private final LacustrineBasinMorphologyAlgorithm basinMorphologyAlgorithm;
    private final LacustrineBasinMorphologyRecipe basinMorphologyRecipe;
    private final StandingWaterHydrologyTopologyStage standingWaterStage;
    private final DrainageBasinTopologyAnalyzer basinAnalyzer;
    private final InlandLakeFormationAlgorithm lakeFormationAlgorithm;
    private final InlandLakeFormationRecipe lakeRecipe;

    public WorldHydrologyTopologyStage(
            LacustrineBasinMorphologyAlgorithm basinMorphologyAlgorithm,
            LacustrineBasinMorphologyRecipe basinMorphologyRecipe,
            StandingWaterHydrologyTopologyStage standingWaterStage,
            DrainageBasinTopologyAnalyzer basinAnalyzer,
            InlandLakeFormationAlgorithm lakeFormationAlgorithm,
            InlandLakeFormationRecipe lakeRecipe) {
        if (basinMorphologyAlgorithm == null
                || basinMorphologyRecipe == null
                || standingWaterStage == null
                || basinAnalyzer == null
                || lakeFormationAlgorithm == null
                || lakeRecipe == null) {
            throw new IllegalArgumentException("world hydrology stage dependencies must not be null");
        }
        this.basinMorphologyAlgorithm = basinMorphologyAlgorithm;
        this.basinMorphologyRecipe = basinMorphologyRecipe;
        this.standingWaterStage = standingWaterStage;
        this.basinAnalyzer = basinAnalyzer;
        this.lakeFormationAlgorithm = lakeFormationAlgorithm;
        this.lakeRecipe = lakeRecipe;
    }

    public static WorldHydrologyTopologyStage standard() {
        return new WorldHydrologyTopologyStage(
                LacustrineBasinMorphologyAlgorithm.standard(),
                LacustrineBasinMorphologyRecipe.balanced(),
                StandingWaterHydrologyTopologyStage.standard(),
                DrainageBasinTopologyAnalyzer.standard(),
                InlandLakeFormationAlgorithm.standard(),
                InlandLakeFormationRecipe.balanced());
    }

    public WorldHydrologyTopology generate(ElevationField baseElevation) {
        if (baseElevation == null) throw new IllegalArgumentException("elevation must not be null");

        LacustrineBasinTerrain basinTerrain = require(
                basinMorphologyAlgorithm.generate(baseElevation, basinMorphologyRecipe),
                "lacustrine basin morphology algorithm");
        ElevationField hydrologicElevation = basinTerrain.elevation();
        StandingWaterHydrologyTopology standingWater = require(
                standingWaterStage.generate(hydrologicElevation),
                "standing-water topology stage");
        DrainageBasinTopology basins = require(
                basinAnalyzer.analyze(hydrologicElevation, standingWater.standingWater()),
                "drainage basin analyzer");
        InlandLakeTopology lakes = require(
                lakeFormationAlgorithm.generate(hydrologicElevation, basins, lakeRecipe),
                "inland lake formation algorithm");

        return new WorldHydrologyTopology(basinTerrain, standingWater, basins, lakes);
    }

    private static <T> T require(T value, String owner) {
        if (value == null) throw new IllegalStateException(owner + " returned null");
        return value;
    }
}
