package io.github.evoforge.simulation.world.terrain.genesis;

import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPageSource;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.terrain.definition.V13MountainDefinition;
import io.github.evoforge.simulation.world.terrain.definition.V15TerrainDefinition;
import io.github.evoforge.simulation.world.terrain.field.ReusableExactTerrainSnapshotPageSource;
import io.github.evoforge.simulation.world.terrain.field.V13ExactMountainPageSource;
import io.github.evoforge.simulation.world.terrain.field.V14ExactCoastalBathymetryPageSource;
import io.github.evoforge.simulation.world.terrain.field.V14ExactDeepBathymetryPageSource;
import io.github.evoforge.simulation.world.terrain.field.V15ExactInlandLakeBathymetryPageSource;

/**
 * Accepted historical V15 terrain composition exposed through Continuum page sources.
 *
 * <p>The declared world domain is the generation domain. V14 coast geometry, V12 relief, V13
 * mountain selection and V15 lake/bathymetry decisions are therefore evaluated with the same world
 * dimensions and coordinate frame as the historical generator. No smaller surrogate world is
 * generated and then stretched over the requested domain.</p>
 *
 * <p>Moderate exact-oracle worlds may retain disposable file-backed stage snapshots so downstream
 * strip/page consumers do not accidentally execute the same historical full-domain pass many times.
 * This is execution reuse only: the historical V15 stage still authors every value.</p>
 */
public final class V15ContinuumTerrainPlan {
    private final ContinuumWorldDomain domain;
    private final V15ContinuumLakeBasePlan lakeBase;
    private final ContinuumScalarPageSource mountains;
    private final ContinuumScalarPageSource coastalBathymetry;
    private final ContinuumScalarPageSource deepBathymetry;
    private final ContinuumScalarPageSource elevationPages;

    private V15ContinuumTerrainPlan(
            ContinuumWorldDomain domain,
            V15ContinuumLakeBasePlan lakeBase,
            ContinuumScalarPageSource mountains,
            ContinuumScalarPageSource coastalBathymetry,
            ContinuumScalarPageSource deepBathymetry,
            ContinuumScalarPageSource elevationPages) {
        this.domain = domain;
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
        long logicalCells = Math.multiplyExact(domain.width(), domain.height());

        V15ContinuumLakeBasePlan lakeBase = V15GenerationProfiler.measure(
                "v15-lake-base-plan",
                logicalCells,
                () -> V15ContinuumLakeBasePlan.prepare(
                        domain,
                        seed,
                        terrainDefinition,
                        maximumZCells));

        V13MountainRecipe mountainRecipe = V13MountainRecipe.balanced();
        V13MountainCalibration mountainCalibration = V13MountainCalibration.compile(
                domain,
                mountainDefinition,
                mountainRecipe,
                maximumZCells);
        V13ExactMountainPageSource rawMountains = V15GenerationProfiler.measure(
                "v13-mountain-plan",
                logicalCells,
                () -> new V13ExactMountainPageSource(
                        domain,
                        seed,
                        lakeBase.elevationPages(),
                        lakeBase.lakeAwareLandRank(),
                        mountainCalibration,
                        mountainRecipe));
        ContinuumScalarPageSource mountains =
                ReusableExactTerrainSnapshotPageSource.captureIfPractical(
                        "v13-mountains",
                        rawMountains);

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
                ReusableExactTerrainSnapshotPageSource.captureIfPractical(
                        "v14-coastal-bathymetry",
                        rawCoastal);

        V14ExactDeepBathymetryPageSource rawDeep = new V14ExactDeepBathymetryPageSource(
                domain,
                coastal,
                bathymetryCalibration,
                bathymetryRecipe);
        ContinuumScalarPageSource deep =
                ReusableExactTerrainSnapshotPageSource.captureIfPractical(
                        "v14-deep-bathymetry",
                        rawDeep);

        V15ExactInlandLakeBathymetryPageSource rawElevationPages =
                new V15ExactInlandLakeBathymetryPageSource(
                        domain,
                        seed,
                        deep,
                        minimumZCells,
                        V15InlandLakeBathymetryRecipe.balanced());
        ContinuumScalarPageSource elevationPages =
                ReusableExactTerrainSnapshotPageSource.captureIfPractical(
                        "v15-inland-bathymetry",
                        rawElevationPages);

        return new V15ContinuumTerrainPlan(
                domain,
                lakeBase,
                mountains,
                coastal,
                deep,
                elevationPages);
    }

    public ContinuumWorldDomain domain() {
        return domain;
    }

    /**
     * Kept as an explicit diagnostic invariant for callers/tests that used the old planning API.
     * The planning domain is now always the actual generation domain.
     */
    public ContinuumWorldDomain planningDomain() {
        return domain;
    }

    /** The scaled-surrogate execution path has been removed. */
    public boolean usesScaledPlanning() {
        return false;
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
