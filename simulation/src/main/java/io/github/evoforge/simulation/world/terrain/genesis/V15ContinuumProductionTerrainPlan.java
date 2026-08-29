package io.github.evoforge.simulation.world.terrain.genesis;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPageSource;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.terrain.definition.V13MountainDefinition;
import io.github.evoforge.simulation.world.terrain.definition.V15TerrainDefinition;
import io.github.evoforge.simulation.world.terrain.field.V13ContinuumMountainPageSource;
import io.github.evoforge.simulation.world.terrain.field.V15ContinuumBathymetryPageSource;
import io.github.evoforge.simulation.world.terrain.field.V15ContinuumLakeBasePageSource;

/**
 * Size-independent production execution of the accepted V12-V15 terrain vocabulary on Continuum.
 *
 * <p>This plan is deliberately separate from {@link V15ContinuumTerrainPlan}, which remains the exact
 * finite-world historical oracle. Global finite-raster decisions that cannot be made without O(area)
 * work are calibrated from fixed-budget samples, while every authored terrain value is evaluated in
 * the declared world's real coordinate frame. No smaller finished terrain or membership raster is
 * scaled onto the requested domain.</p>
 */
public final class V15ContinuumProductionTerrainPlan {
    private static final int PPM = NormalizedValue.SCALE;
    private static final int HISTORICAL_V12_BASE_CEILING_CELLS = 12;

    private final ContinuumWorldDomain domain;
    private final V14ContinuumBaseTerrainPlan continental;
    private final V15ContinuumLakeDomainPlan lakes;
    private final V12LandRankPlan lakeAwareLand;
    private final ContinuumScalarPageSource lakeBase;
    private final ContinuumScalarPageSource mountains;
    private final ContinuumScalarPageSource elevationPages;

    private V15ContinuumProductionTerrainPlan(
            ContinuumWorldDomain domain,
            V14ContinuumBaseTerrainPlan continental,
            V15ContinuumLakeDomainPlan lakes,
            V12LandRankPlan lakeAwareLand,
            ContinuumScalarPageSource lakeBase,
            ContinuumScalarPageSource mountains,
            ContinuumScalarPageSource elevationPages) {
        this.domain = domain;
        this.continental = continental;
        this.lakes = lakes;
        this.lakeAwareLand = lakeAwareLand;
        this.lakeBase = lakeBase;
        this.mountains = mountains;
        this.elevationPages = elevationPages;
    }

    public static V15ContinuumProductionTerrainPlan prepare(
            ContinuumWorldDomain domain,
            long seed,
            V15TerrainDefinition terrainDefinition,
            V13MountainDefinition mountainDefinition,
            int minimumZCells,
            int maximumZCells) {
        if (domain == null || terrainDefinition == null || mountainDefinition == null) {
            throw new IllegalArgumentException("V15 Continuum production inputs must not be null");
        }
        long logicalCells = Math.multiplyExact(domain.width(), domain.height());
        V15TerrainDefinition placementDefinition = predictedContinentalDefinition(terrainDefinition);

        V14ContinuumBaseTerrainPlan continental = V15GenerationProfiler.measure(
                "v15-continuum-base-plan",
                logicalCells,
                () -> V14ContinuumBaseTerrainPlan.prepareContinuum(
                        domain,
                        seed,
                        placementDefinition,
                        HISTORICAL_V12_BASE_CEILING_CELLS));

        V15ContinuumLakeDomainPlan lakes = V15GenerationProfiler.measure(
                "v15-continuum-lake-calibration",
                logicalCells,
                () -> V15ContinuumLakeDomainPlan.prepare(
                        domain,
                        seed,
                        continental,
                        maximumZCells));
        V12LandRankPlan lakeAwareLand = continental.landRank().excluding(
                Math.min(continental.landRank().landCount(), lakes.targetLakeCells()),
                lakes::isLake);
        ContinuumScalarPageSource lakeBase = new V15ContinuumLakeBasePageSource(
                domain,
                continental.elevationPages(),
                lakes);
        ContinuumScalarPageSource mountains = new V13ContinuumMountainPageSource(
                domain,
                seed,
                mountainDefinition,
                lakeBase,
                lakeAwareLand,
                maximumZCells);
        ContinuumScalarPageSource bathymetry = new V15ContinuumBathymetryPageSource(
                domain,
                seed,
                mountains,
                lakes,
                minimumZCells);

        return new V15ContinuumProductionTerrainPlan(
                domain,
                continental,
                lakes,
                lakeAwareLand,
                lakeBase,
                mountains,
                bathymetry);
    }

    public ContinuumWorldDomain domain() {
        return domain;
    }

    public V14ContinuumBaseTerrainPlan continental() {
        return continental;
    }

    public V15ContinuumLakeDomainPlan lakes() {
        return lakes;
    }

    public V12LandRankPlan lakeAwareLand() {
        return lakeAwareLand;
    }

    public ContinuumScalarPageSource lakeBasePages() {
        return lakeBase;
    }

    public ContinuumScalarPageSource mountainPages() {
        return mountains;
    }

    public ContinuumScalarPageSource elevationPages() {
        return elevationPages;
    }

    private static V15TerrainDefinition predictedContinentalDefinition(V15TerrainDefinition definition) {
        int lakeCoveragePpm = V15InlandLakeDomainRecipe.balanced().targetDryLandCoveragePpm();
        if (lakeCoveragePpm <= 0) return definition;
        long denominator = PPM - (long) lakeCoveragePpm;
        long dryPpm = definition.landCoverage().partsPerMillion();
        int continentalPpm = Math.toIntExact(Math.min(
                (long) PPM,
                (dryPpm * PPM + denominator / 2L) / denominator));
        return new V15TerrainDefinition(
                NormalizedValue.ofPartsPerMillion(continentalPpm),
                definition.landmassScale(),
                definition.fragmentation(),
                definition.relief(),
                definition.localRelief(),
                definition.landformScale(),
                definition.ruggedness());
    }
}
