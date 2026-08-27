package io.github.evoforge.simulation.world.terrain.genesis;

import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPageSource;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.terrain.definition.V13MountainDefinition;
import io.github.evoforge.simulation.world.terrain.definition.V15TerrainDefinition;
import io.github.evoforge.simulation.world.terrain.field.V12ExactSlopePageSource;
import io.github.evoforge.simulation.world.terrain.field.V12UnrelaxedLandElevationField;
import io.github.evoforge.simulation.world.terrain.field.V13ExactMountainPageSource;

/** Exact accepted V13 composition expressed through Continuum page sources. */
public final class V13ContinuumTerrainPlan {
    private final ContinuumWorldDomain domain;
    private final long seed;
    private final V12LandRankPlan landRank;
    private final V12ExactSlopePageSource v12Base;
    private final V13MountainCalibration mountainCalibration;
    private final V13ExactMountainPageSource elevationPages;

    private V13ContinuumTerrainPlan(
            ContinuumWorldDomain domain,
            long seed,
            V12LandRankPlan landRank,
            V12ExactSlopePageSource v12Base,
            V13MountainCalibration mountainCalibration,
            V13ExactMountainPageSource elevationPages) {
        this.domain = domain;
        this.seed = seed;
        this.landRank = landRank;
        this.v12Base = v12Base;
        this.mountainCalibration = mountainCalibration;
        this.elevationPages = elevationPages;
    }

    public static V13ContinuumTerrainPlan prepare(
            ContinuumWorldDomain domain,
            long seed,
            V15TerrainDefinition terrainDefinition,
            V13MountainDefinition mountainDefinition,
            int minimumZCells,
            int maximumZCells) {
        if (domain == null || terrainDefinition == null || mountainDefinition == null) {
            throw new IllegalArgumentException("V13 Continuum inputs must not be null");
        }
        if (minimumZCells >= 0) {
            throw new IllegalArgumentException("V13 terrain requires bounds below sea level");
        }

        V13MountainRecipe mountainRecipe = V13MountainRecipe.balanced();
        V13MountainCalibration mountainCalibration = V13MountainCalibration.compile(
                domain, mountainDefinition, mountainRecipe, maximumZCells);

        V12TerrainRecipe v12Recipe = V12TerrainRecipe.balanced();
        V12TerrainCalibration v12Calibration = V12TerrainCalibration.compile(
                domain, terrainDefinition, v12Recipe);
        V12LandRankPlan landRank = V12LandRankPlan.prepareUnconstrained(
                domain, seed, v12Calibration, v12Recipe);
        V12UnrelaxedLandElevationField unrelaxed = new V12UnrelaxedLandElevationField(
                domain,
                seed,
                landRank,
                v12Calibration,
                v12Recipe,
                mountainRecipe.baseTerrainCeilingCells());
        V12ContinuumSlopeCalibration slope = V12ContinuumSlopeCalibration.compile(
                v12Calibration,
                v12Recipe,
                mountainRecipe.baseTerrainCeilingCells());
        V12ExactSlopePageSource v12Base = new V12ExactSlopePageSource(
                domain,
                unrelaxed,
                landRank,
                slope,
                v12Recipe,
                minimumZCells);
        V13ExactMountainPageSource elevationPages = new V13ExactMountainPageSource(
                domain,
                seed,
                v12Base,
                landRank,
                mountainCalibration,
                mountainRecipe);

        return new V13ContinuumTerrainPlan(
                domain,
                seed,
                landRank,
                v12Base,
                mountainCalibration,
                elevationPages);
    }

    public ContinuumWorldDomain domain() {
        return domain;
    }

    public long seed() {
        return seed;
    }

    public V12LandRankPlan landRank() {
        return landRank;
    }

    public ContinuumScalarPageSource v12BasePages() {
        return v12Base;
    }

    public V13MountainCalibration mountainCalibration() {
        return mountainCalibration;
    }

    public ContinuumScalarPageSource elevationPages() {
        return elevationPages;
    }
}
