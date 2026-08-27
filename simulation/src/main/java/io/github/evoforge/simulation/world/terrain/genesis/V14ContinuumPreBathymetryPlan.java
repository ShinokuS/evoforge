package io.github.evoforge.simulation.world.terrain.genesis;

import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPageSource;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.terrain.definition.V13MountainDefinition;
import io.github.evoforge.simulation.world.terrain.definition.V15TerrainDefinition;
import io.github.evoforge.simulation.world.terrain.field.V13ExactMountainPageSource;

/**
 * Exact historical V14 terrain immediately before bathymetry: oceanic V12 footprint plus V13
 * mountain morphology. This mirrors the base passed into {@code V14BathymetryTerrainGenerator}.
 */
public final class V14ContinuumPreBathymetryPlan {
    private static final int HISTORICAL_V12_BASE_CEILING_CELLS = 12;

    private final V14ContinuumBaseTerrainPlan oceanicV12;
    private final V13MountainCalibration mountainCalibration;
    private final V13ExactMountainPageSource elevationPages;

    private V14ContinuumPreBathymetryPlan(
            V14ContinuumBaseTerrainPlan oceanicV12,
            V13MountainCalibration mountainCalibration,
            V13ExactMountainPageSource elevationPages) {
        this.oceanicV12 = oceanicV12;
        this.mountainCalibration = mountainCalibration;
        this.elevationPages = elevationPages;
    }

    public static V14ContinuumPreBathymetryPlan prepare(
            ContinuumWorldDomain domain,
            long seed,
            V15TerrainDefinition terrainDefinition,
            V13MountainDefinition mountainDefinition,
            int maximumZCells) {
        if (domain == null || terrainDefinition == null || mountainDefinition == null) {
            throw new IllegalArgumentException("V14 pre-bathymetry inputs must not be null");
        }

        V14ContinuumBaseTerrainPlan oceanicV12 = V14ContinuumBaseTerrainPlan.prepare(
                domain,
                seed,
                terrainDefinition,
                HISTORICAL_V12_BASE_CEILING_CELLS);
        V13MountainRecipe mountainRecipe = V13MountainRecipe.balanced();
        V13MountainCalibration mountainCalibration = V13MountainCalibration.compile(
                domain,
                mountainDefinition,
                mountainRecipe,
                maximumZCells);
        V13ExactMountainPageSource elevationPages = new V13ExactMountainPageSource(
                domain,
                seed,
                oceanicV12.elevationPages(),
                oceanicV12.landRank(),
                mountainCalibration,
                mountainRecipe);
        return new V14ContinuumPreBathymetryPlan(
                oceanicV12,
                mountainCalibration,
                elevationPages);
    }

    public V14ContinuumBaseTerrainPlan oceanicV12() {
        return oceanicV12;
    }

    public V13MountainCalibration mountainCalibration() {
        return mountainCalibration;
    }

    public ContinuumScalarPageSource elevationPages() {
        return elevationPages;
    }
}
