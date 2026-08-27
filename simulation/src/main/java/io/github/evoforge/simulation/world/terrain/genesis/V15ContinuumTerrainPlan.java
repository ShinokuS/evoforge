package io.github.evoforge.simulation.world.terrain.genesis;

import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPageSource;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.terrain.definition.V13MountainDefinition;
import io.github.evoforge.simulation.world.terrain.definition.V15TerrainDefinition;
import io.github.evoforge.simulation.world.terrain.field.BoundedExactTerrainSnapshotPageSource;
import io.github.evoforge.simulation.world.terrain.field.V13ExactMountainPageSource;
import io.github.evoforge.simulation.world.terrain.field.V14ExactCoastalBathymetryPageSource;
import io.github.evoforge.simulation.world.terrain.field.V14ExactDeepBathymetryPageSource;
import io.github.evoforge.simulation.world.terrain.field.V15ExactInlandLakeBathymetryPageSource;

/** Exact accepted V15 composition through inland-lake depth refinement. */
public final class V15ContinuumTerrainPlan {
    private final V15ContinuumLakeBasePlan lakeBase;
    private final V13ExactMountainPageSource mountains;
    private final ContinuumScalarPageSource coastalBathymetry;
    private final ContinuumScalarPageSource deepBathymetry;
    private final ContinuumScalarPageSource elevationPages;

    private V15ContinuumTerrainPlan(
            V15ContinuumLakeBasePlan lakeBase,
            V13ExactMountainPageSource mountains,
            ContinuumScalarPageSource coastalBathymetry,
            ContinuumScalarPageSource deepBathymetry,
            ContinuumScalarPageSource elevationPages) {
        this.lakeBase = lakeBase;
        this.mountains = mountains;
        this.coastalBathymetry = coastalBathymetry;
        this.deepBathymetry = deepBathymetry;
        this.elevationPages = elevationPages;
    }

    public static V15ContinuumTerrainPlan prepare(
            ContinuumWorldDomain domain,
            long seed,
            V15TerrainDefinition terrainDefinition,
            V13MountainDefinition mountainDefinition,
            int minimumZCells,
            int maximumZCells) {
        if (domain == null || terrainDefinition == null || mountainDefinition == null) {
            throw new IllegalArgumentException("V15 Continuum terrain inputs must not be null");
        }
        V15ContinuumLakeBasePlan lakeBase = V15ContinuumLakeBasePlan.prepare(
                domain,
                seed,
                terrainDefinition,
                maximumZCells);

        V13MountainRecipe mountainRecipe = V13MountainRecipe.balanced();
        V13MountainCalibration mountainCalibration = V13MountainCalibration.compile(
                domain,
                mountainDefinition,
                mountainRecipe,
                maximumZCells);
        V13ExactMountainPageSource mountains = new V13ExactMountainPageSource(
                domain,
                seed,
                lakeBase.elevationPages(),
                lakeBase.lakeAwareLandRank(),
                mountainCalibration,
                mountainRecipe);

        V14BathymetryRecipe bathymetryRecipe = V14BathymetryRecipe.balanced();
        V14BathymetryCalibration bathymetryCalibration = V14BathymetryCalibration.compile(
                domain,
                minimumZCells,
                bathymetryRecipe);
        V14ExactCoastalBathymetryPageSource rawCoastal = new V14ExactCoastalBathymetryPageSource(
                domain,
                mountains,
                bathymetryCalibration,
                bathymetryRecipe);
        ContinuumScalarPageSource coastal =
                BoundedExactTerrainSnapshotPageSource.captureIfBounded(rawCoastal);

        V14ExactDeepBathymetryPageSource rawDeep = new V14ExactDeepBathymetryPageSource(
                domain,
                coastal,
                bathymetryCalibration,
                bathymetryRecipe);
        ContinuumScalarPageSource deep =
                BoundedExactTerrainSnapshotPageSource.captureIfBounded(rawDeep);

        V15ExactInlandLakeBathymetryPageSource rawElevationPages =
                new V15ExactInlandLakeBathymetryPageSource(
                        domain,
                        seed,
                        deep,
                        minimumZCells,
                        V15InlandLakeBathymetryRecipe.balanced());
        ContinuumScalarPageSource elevationPages =
                BoundedExactTerrainSnapshotPageSource.captureIfBounded(rawElevationPages);
        return new V15ContinuumTerrainPlan(lakeBase, mountains, coastal, deep, elevationPages);
    }

    public V15ContinuumLakeBasePlan lakeBase() {
        return lakeBase;
    }

    public ContinuumScalarPageSource mountainPages() {
        return mountains;
    }

    public ContinuumScalarPageSource coastalBathymetryPages() {
        return coastalBathymetry;
    }

    public ContinuumScalarPageSource deepBathymetryPages() {
        return deepBathymetry;
    }

    public ContinuumScalarPageSource elevationPages() {
        return elevationPages;
    }
}
