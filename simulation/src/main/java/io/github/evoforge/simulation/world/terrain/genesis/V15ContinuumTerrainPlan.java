package io.github.evoforge.simulation.world.terrain.genesis;

import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPageSource;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.terrain.definition.V13MountainDefinition;
import io.github.evoforge.simulation.world.terrain.definition.V15TerrainDefinition;
import io.github.evoforge.simulation.world.terrain.field.BoundedExactTerrainSnapshotPageSource;
import io.github.evoforge.simulation.world.terrain.field.V13ExactMountainPageSource;
import io.github.evoforge.simulation.world.terrain.field.V14ExactCoastalBathymetryPageSource;
import io.github.evoforge.simulation.world.terrain.field.V14ExactDeepBathymetryPageSource;
import io.github.evoforge.simulation.world.terrain.field.V15ContinuumScaledPageSource;
import io.github.evoforge.simulation.world.terrain.field.V15ExactInlandLakeBathymetryPageSource;

/**
 * Accepted V15 terrain composition exposed through a bounded Continuum execution model.
 *
 * <p>Domains up to {@link #MAX_EXACT_PLANNING_AXIS} retain the historical cell-for-cell execution.
 * Larger declared worlds reuse the same V15 composition on a bounded, aspect-preserving planning
 * domain and expose that morphology through a lazy scaled page source. Declaring a larger world
 * therefore does not ask V12-V15 to rasterize every simulation cell during preparation.</p>
 */
public final class V15ContinuumTerrainPlan {
    /**
     * The largest axis for which production executes the old finite raster semantics directly.
     *
     * <p>300 deliberately covers the live 300 x 300 historical V15 oracle. Larger worlds keep V15 as
     * their morphology authority but decouple simulation resolution from global planning resolution.
     */
    public static final int MAX_EXACT_PLANNING_AXIS = 300;
    private static final int MIN_PLANNING_AXIS = 8;

    private final ContinuumWorldDomain domain;
    private final ContinuumWorldDomain planningDomain;
    private final V15ContinuumLakeBasePlan lakeBase;
    private final ContinuumScalarPageSource mountains;
    private final ContinuumScalarPageSource coastalBathymetry;
    private final ContinuumScalarPageSource deepBathymetry;
    private final ContinuumScalarPageSource elevationPages;

    private V15ContinuumTerrainPlan(
            ContinuumWorldDomain domain,
            ContinuumWorldDomain planningDomain,
            V15ContinuumLakeBasePlan lakeBase,
            ContinuumScalarPageSource mountains,
            ContinuumScalarPageSource coastalBathymetry,
            ContinuumScalarPageSource deepBathymetry,
            ContinuumScalarPageSource elevationPages) {
        this.domain = domain;
        this.planningDomain = planningDomain;
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
        if (Math.max(domain.width(), domain.height()) <= MAX_EXACT_PLANNING_AXIS) {
            return prepareExact(
                    domain,
                    seed,
                    terrainDefinition,
                    mountainDefinition,
                    minimumZCells,
                    maximumZCells);
        }

        ContinuumWorldDomain planningDomain = boundedPlanningDomain(domain);
        V15ContinuumTerrainPlan planning = prepareExact(
                planningDomain,
                seed,
                terrainDefinition,
                mountainDefinition,
                minimumZCells,
                maximumZCells);
        return new V15ContinuumTerrainPlan(
                domain,
                planningDomain,
                planning.lakeBase,
                scaled(domain, planning.mountains),
                scaled(domain, planning.coastalBathymetry),
                scaled(domain, planning.deepBathymetry),
                scaled(domain, planning.elevationPages));
    }

    private static V15ContinuumTerrainPlan prepareExact(
            ContinuumWorldDomain domain,
            long seed,
            V15TerrainDefinition terrainDefinition,
            V13MountainDefinition mountainDefinition,
            int minimumZCells,
            int maximumZCells) {
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
        return new V15ContinuumTerrainPlan(
                domain,
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

    /** Bounded V15 domain that owns global morphology decisions. */
    public ContinuumWorldDomain planningDomain() {
        return planningDomain;
    }

    public boolean usesScaledPlanning() {
        return !domain.equals(planningDomain);
    }

    /**
     * Exact V15 planning internals. On a scaled world these belong to {@link #planningDomain()}, not
     * the larger presentation/simulation domain.
     */
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

    private static ContinuumScalarPageSource scaled(
            ContinuumWorldDomain domain,
            ContinuumScalarPageSource planningSource) {
        return new V15ContinuumScaledPageSource(domain, planningSource);
    }

    private static ContinuumWorldDomain boundedPlanningDomain(ContinuumWorldDomain domain) {
        double scale = MAX_EXACT_PLANNING_AXIS
                / (double) Math.max(domain.width(), domain.height());
        long width = Math.max(
                MIN_PLANNING_AXIS,
                Math.min(
                        MAX_EXACT_PLANNING_AXIS,
                        Math.round(domain.width() * scale)));
        long height = Math.max(
                MIN_PLANNING_AXIS,
                Math.min(
                        MAX_EXACT_PLANNING_AXIS,
                        Math.round(domain.height() * scale)));
        return new ContinuumWorldDomain(width, height);
    }
}
