package io.github.evoforge.simulation.world.terrain.genesis;

import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPageSource;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.terrain.definition.V15TerrainDefinition;
import io.github.evoforge.simulation.world.terrain.field.V12HistoricalSlopePageSource;
import io.github.evoforge.simulation.world.terrain.field.V12UnrelaxedLandElevationField;

/**
 * Prepared Continuum equivalent of the accepted historical V14 oceanic base-terrain composition.
 *
 * <p>The exact finite-world constructor preserves historical global cutoff/rank decisions. The
 * large-domain {@link #prepareContinuum} constructor keeps the same V14/V12 authored coordinate laws
 * but obtains only their global scalar cutoff/rank facts through fixed-budget sampling. Both expose
 * the accepted V12 relief and bounded historical slope execution on demand.</p>
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

    /** Historical exact finite-world preparation. */
    public static V14ContinuumBaseTerrainPlan prepare(
            ContinuumWorldDomain domain,
            long seed,
            V15TerrainDefinition definition,
            int maximumLandHeightCells) {
        return prepareInternal(domain, seed, definition, maximumLandHeightCells, null, false);
    }

    /** Fixed-budget large-domain preparation in the declared world's real coordinate frame. */
    public static V14ContinuumBaseTerrainPlan prepareContinuum(
            ContinuumWorldDomain domain,
            long seed,
            V15TerrainDefinition definition,
            int maximumLandHeightCells) {
        return prepareInternal(domain, seed, definition, maximumLandHeightCells, null, true);
    }

    static V14ContinuumBaseTerrainPlan prepare(
            ContinuumWorldDomain domain,
            long seed,
            V15TerrainDefinition definition,
            int maximumLandHeightCells,
            V14ContinuumBaseTerrainPlan reusablePotentialSource) {
        return prepareInternal(
                domain,
                seed,
                definition,
                maximumLandHeightCells,
                reusablePotentialSource,
                reusablePotentialSource != null && reusablePotentialSource.landRank().usesSampledRank());
    }

    static V14ContinuumBaseTerrainPlan prepareContinuum(
            ContinuumWorldDomain domain,
            long seed,
            V15TerrainDefinition definition,
            int maximumLandHeightCells,
            V14ContinuumBaseTerrainPlan reusablePotentialSource) {
        return prepareInternal(
                domain,
                seed,
                definition,
                maximumLandHeightCells,
                reusablePotentialSource,
                true);
    }

    private static V14ContinuumBaseTerrainPlan prepareInternal(
            ContinuumWorldDomain domain,
            long seed,
            V15TerrainDefinition definition,
            int maximumLandHeightCells,
            V14ContinuumBaseTerrainPlan reusablePotentialSource,
            boolean sampledCalibration) {
        if (domain == null || definition == null) {
            throw new IllegalArgumentException("V14 Continuum base-terrain inputs must not be null");
        }
        if (maximumLandHeightCells <= 0) {
            throw new IllegalArgumentException("maximumLandHeightCells must be > 0");
        }
        if (reusablePotentialSource != null) {
            if (!domain.equals(reusablePotentialSource.domain())) {
                throw new IllegalArgumentException(
                        "reusable V14 base must match the requested base-terrain domain");
            }
            if (seed != reusablePotentialSource.seed()) {
                throw new IllegalArgumentException("reusable V14 base must use the same seed");
            }
            if (definition.landmassScale().partsPerMillion()
                            != reusablePotentialSource.definition().landmassScale().partsPerMillion()
                    || definition.fragmentation().partsPerMillion()
                            != reusablePotentialSource.definition().fragmentation().partsPerMillion()) {
                throw new IllegalArgumentException(
                        "reusable V14 base must preserve landmass-scale and fragmentation authorship");
            }
            if (sampledCalibration != reusablePotentialSource.landRank().usesSampledRank()) {
                throw new IllegalArgumentException(
                        "reusable V14 base must use the same exact/Continuum rank mode");
            }
        }

        long logicalCells = Math.multiplyExact(domain.width(), domain.height());
        V12TerrainRecipe recipe = V12TerrainRecipe.balanced();
        V12TerrainCalibration terrain = V12TerrainCalibration.compile(domain, definition, recipe);
        V14LandmassPlan landmass = reusablePotentialSource != null
                ? reusablePotentialSource.landmass()
                : V15GenerationProfiler.measure(
                        sampledCalibration
                                ? "v14-continuum-landmass-calibration"
                                : "v14-landmass-cutoff",
                        logicalCells,
                        () -> sampledCalibration
                                ? V14LandmassPlan.prepareContinuum(domain, seed, definition, terrain)
                                : V14LandmassPlan.prepare(domain, seed, definition, terrain));
        V12LandRankPlan landRank = reusablePotentialSource != null
                ? V15GenerationProfiler.measure(
                        sampledCalibration ? "v12-continuum-rerank" : "v12-land-rerank",
                        logicalCells,
                        () -> reusablePotentialSource.landRank().rerank(terrain))
                : V15GenerationProfiler.measure(
                        sampledCalibration
                                ? "v12-continuum-rank-calibration"
                                : "v12-land-rank",
                        logicalCells,
                        () -> sampledCalibration
                                ? V12LandRankPlan.prepareConstrainedContinuum(
                                        domain, seed, terrain, recipe, landmass)
                                : V12LandRankPlan.prepareConstrained(
                                        domain, seed, terrain, recipe, landmass));
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
