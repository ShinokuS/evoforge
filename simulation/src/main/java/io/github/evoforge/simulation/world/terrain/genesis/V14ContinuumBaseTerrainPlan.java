package io.github.evoforge.simulation.world.terrain.genesis;

import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPageSource;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.terrain.definition.V15TerrainDefinition;
import io.github.evoforge.simulation.world.terrain.field.V12HistoricalSlopePageSource;
import io.github.evoforge.simulation.world.terrain.field.V12UnrelaxedLandElevationField;

/**
 * Prepared Continuum equivalent of the accepted historical V14 oceanic base-terrain composition.
 *
 * <p>Preparation keeps only deterministic global facts needed by the historical algorithm: V12
 * calibration, the V14 control graph and cutoffs, and the V12 rank threshold/tie boundary. Requested
 * pages execute the accepted V12 relief and the historical ordered slope relaxation inside the
 * validated bounded migration halo. V14 later re-authors all standing-water bathymetry, so this
 * source needs to preserve V12 land heights and land/water membership rather than retain the old
 * globally ranked temporary ocean depths.</p>
 */
public final class V14ContinuumBaseTerrainPlan {
    private final ContinuumWorldDomain domain;
    private final long seed;
    private final V15TerrainDefinition definition;
    private final V12TerrainRecipe recipe;
    private final V12TerrainCalibration terrain;
    private final V14LandmassPlan landmass;
    private final V12LandRankPlan landRank;
    private final V12UnrelaxedLandElevationField unrelaxedElevation;
    private final V12ContinuumSlopeCalibration slope;
    private final V12HistoricalSlopePageSource elevationPages;

    private V14ContinuumBaseTerrainPlan(
            ContinuumWorldDomain domain,
            long seed,
            V15TerrainDefinition definition,
            V12TerrainRecipe recipe,
            V12TerrainCalibration terrain,
            V14LandmassPlan landmass,
            V12LandRankPlan landRank,
            V12UnrelaxedLandElevationField unrelaxedElevation,
            V12ContinuumSlopeCalibration slope,
            V12HistoricalSlopePageSource elevationPages) {
        this.domain = domain;
        this.seed = seed;
        this.definition = definition;
        this.recipe = recipe;
        this.terrain = terrain;
        this.landmass = landmass;
        this.landRank = landRank;
        this.unrelaxedElevation = unrelaxedElevation;
        this.slope = slope;
        this.elevationPages = elevationPages;
    }

    public static V14ContinuumBaseTerrainPlan prepare(
            ContinuumWorldDomain domain,
            long seed,
            V15TerrainDefinition definition,
            int maximumLandHeightCells) {
        if (domain == null || definition == null) {
            throw new IllegalArgumentException("V14 Continuum base-terrain inputs must not be null");
        }
        if (maximumLandHeightCells <= 0) {
            throw new IllegalArgumentException("maximumLandHeightCells must be > 0");
        }

        V12TerrainRecipe recipe = V12TerrainRecipe.balanced();
        V12TerrainCalibration terrain = V12TerrainCalibration.compile(domain, definition, recipe);
        V14LandmassPlan landmass = V14LandmassPlan.prepare(domain, seed, definition, terrain);
        V12LandRankPlan landRank = V12LandRankPlan.prepareConstrained(
                domain, seed, terrain, recipe, landmass);
        V12UnrelaxedLandElevationField unrelaxed = new V12UnrelaxedLandElevationField(
                domain,
                seed,
                landRank,
                terrain,
                recipe,
                maximumLandHeightCells);
        V12ContinuumSlopeCalibration slope = V12ContinuumSlopeCalibration.compile(
                terrain, recipe, maximumLandHeightCells);
        V12HistoricalSlopePageSource elevationPages = new V12HistoricalSlopePageSource(
                domain,
                unrelaxed,
                slope,
                recipe);

        return new V14ContinuumBaseTerrainPlan(
                domain,
                seed,
                definition,
                recipe,
                terrain,
                landmass,
                landRank,
                unrelaxed,
                slope,
                elevationPages);
    }

    public ContinuumWorldDomain domain() {
        return domain;
    }

    public long seed() {
        return seed;
    }

    public V15TerrainDefinition definition() {
        return definition;
    }

    public V12TerrainRecipe recipe() {
        return recipe;
    }

    public V12TerrainCalibration terrainCalibration() {
        return terrain;
    }

    public V14LandmassPlan landmass() {
        return landmass;
    }

    public V12LandRankPlan landRank() {
        return landRank;
    }

    public V12UnrelaxedLandElevationField unrelaxedElevation() {
        return unrelaxedElevation;
    }

    public V12ContinuumSlopeCalibration slopeCalibration() {
        return slope;
    }

    public ContinuumScalarPageSource elevationPages() {
        return elevationPages;
    }
}
