package io.github.evoforge.simulation.world.terrain.genesis;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPageSource;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.terrain.definition.V15TerrainDefinition;
import io.github.evoforge.simulation.world.terrain.field.ReusableExactTerrainSnapshotPageSource;
import io.github.evoforge.simulation.world.terrain.field.V15ExactInlandLakeBasePageSource;
import io.github.evoforge.simulation.world.terrain.field.V15InlandLakeDomainPlan;

/** Exact accepted V15 pre-mountain composition, including predictive lake land reservation. */
public final class V15ContinuumLakeBasePlan {
    private static final int PPM = NormalizedValue.SCALE;
    private static final int HISTORICAL_V12_BASE_CEILING_CELLS = 12;

    private final V14ContinuumBaseTerrainPlan continental;
    private final V15InlandLakeDomainPlan lakeDomain;
    private final V12LandRankPlan lakeAwareLandRank;
    private final ContinuumScalarPageSource elevationPages;

    private V15ContinuumLakeBasePlan(
            V14ContinuumBaseTerrainPlan continental,
            V15InlandLakeDomainPlan lakeDomain,
            V12LandRankPlan lakeAwareLandRank,
            ContinuumScalarPageSource elevationPages) {
        this.continental = continental;
        this.lakeDomain = lakeDomain;
        this.lakeAwareLandRank = lakeAwareLandRank;
        this.elevationPages = elevationPages;
    }

    public static V15ContinuumLakeBasePlan prepare(
            ContinuumWorldDomain domain,
            long seed,
            V15TerrainDefinition definition,
            int maximumZCells) {
        if (domain == null || definition == null) {
            throw new IllegalArgumentException("V15 lake-base inputs must not be null");
        }
        long logicalCells = Math.multiplyExact(domain.width(), domain.height());
        V15InlandLakeDomainRecipe recipe = V15InlandLakeDomainRecipe.balanced();
        V15TerrainDefinition placementDefinition = predictedLandDefinition(domain, definition, recipe);
        V14ContinuumBaseTerrainPlan placement = V15GenerationProfiler.measure(
                "v14-placement-base",
                logicalCells,
                () -> V14ContinuumBaseTerrainPlan.prepare(
                        domain,
                        seed,
                        placementDefinition,
                        HISTORICAL_V12_BASE_CEILING_CELLS));
        ContinuumScalarPageSource placementPages =
                ReusableExactTerrainSnapshotPageSource.captureIfPractical(
                        "v12-placement-slope",
                        placement.elevationPages());
        V15InlandLakeDomainPlan lakeDomain = V15GenerationProfiler.measure(
                "v15-lake-domain",
                logicalCells,
                () -> V15InlandLakeDomainPlan.prepare(
                        domain,
                        placementPages,
                        maximumZCells,
                        recipe));

        V15TerrainDefinition exactDefinition = lakeDomain.lakeCellCount() == 0
                ? definition
                : compensatedLandDefinition(domain, definition, lakeDomain.lakeCellCount());
        V14ContinuumBaseTerrainPlan authoritative;
        ContinuumScalarPageSource authoritativePages;
        if (sameLandCoverage(placementDefinition, exactDefinition)) {
            authoritative = placement;
            authoritativePages = placementPages;
        } else {
            authoritative = V15GenerationProfiler.measure(
                    "v14-authoritative-base",
                    logicalCells,
                    () -> V14ContinuumBaseTerrainPlan.prepare(
                            domain,
                            seed,
                            exactDefinition,
                            HISTORICAL_V12_BASE_CEILING_CELLS,
                            placement.landmass()));
            authoritativePages = ReusableExactTerrainSnapshotPageSource.captureIfPractical(
                    "v12-authoritative-slope",
                    authoritative.elevationPages());
        }

        ContinuumScalarPageSource verifiedAuthoritativePages = authoritativePages;
        V15GenerationProfiler.measure(
                "v15-lake-dry-support",
                logicalCells,
                () -> {
                    lakeDomain.verifyDrySupport(verifiedAuthoritativePages);
                    return Boolean.TRUE;
                });
        V12LandRankPlan lakeAwareLandRank = authoritative.landRank().excluding(
                lakeDomain.lakeCellCount(),
                lakeDomain::isLake);
        V15ExactInlandLakeBasePageSource rawElevationPages = new V15ExactInlandLakeBasePageSource(
                domain,
                authoritativePages,
                lakeDomain);
        ContinuumScalarPageSource elevationPages =
                ReusableExactTerrainSnapshotPageSource.captureIfPractical(
                        "v15-lake-base",
                        rawElevationPages);
        return new V15ContinuumLakeBasePlan(
                authoritative, lakeDomain, lakeAwareLandRank, elevationPages);
    }

    public V14ContinuumBaseTerrainPlan continental() {
        return continental;
    }

    public V15InlandLakeDomainPlan lakeDomain() {
        return lakeDomain;
    }

    public V12LandRankPlan lakeAwareLandRank() {
        return lakeAwareLandRank;
    }

    public ContinuumScalarPageSource elevationPages() {
        return elevationPages;
    }

    private static V15TerrainDefinition predictedLandDefinition(
            ContinuumWorldDomain domain,
            V15TerrainDefinition definition,
            V15InlandLakeDomainRecipe recipe) {
        int lakeCoveragePpm = recipe.targetDryLandCoveragePpm();
        if (lakeCoveragePpm <= 0) return definition;
        int area = area(domain);
        int desiredDryCells = desiredDryCells(definition, area);
        long denominator = PPM - (long) lakeCoveragePpm;
        if (denominator <= 0L) return withLandCoverage(definition, PPM);

        int predictedLakeCells = Math.toIntExact(
                ((long) desiredDryCells * lakeCoveragePpm + denominator / 2L) / denominator);
        int limitingSpan = Math.min(Math.toIntExact(domain.width()), Math.toIntExact(domain.height()));
        int minimumSpan = Math.max(
                recipe.minimumComponentSpanCells(),
                limitingSpan / recipe.componentSpanWorldDivisor());
        int minimumLakeCells = Math.max(4, minimumSpan * minimumSpan / 2);
        if (desiredDryCells > 0) predictedLakeCells = Math.max(predictedLakeCells, minimumLakeCells);

        int predictedContinentalCells = Math.min(
                area,
                Math.addExact(
                        desiredDryCells,
                        Math.min(predictedLakeCells, area - desiredDryCells)));
        return withContinentalCells(definition, predictedContinentalCells, area);
    }

    private static V15TerrainDefinition compensatedLandDefinition(
            ContinuumWorldDomain domain,
            V15TerrainDefinition definition,
            int lakeCellCount) {
        int area = area(domain);
        int desiredDryCells = desiredDryCells(definition, area);
        int continentalCells = Math.min(area, Math.addExact(desiredDryCells, lakeCellCount));
        return withContinentalCells(definition, continentalCells, area);
    }

    private static int desiredDryCells(V15TerrainDefinition definition, int area) {
        return Math.toIntExact(
                ((long) area * definition.landCoverage().partsPerMillion() + PPM / 2L) / PPM);
    }

    private static V15TerrainDefinition withContinentalCells(
            V15TerrainDefinition definition,
            int continentalCells,
            int area) {
        int coveragePpm = Math.toIntExact(Math.min(
                (long) PPM,
                ((long) continentalCells * PPM + area / 2L) / area));
        return withLandCoverage(definition, coveragePpm);
    }

    private static V15TerrainDefinition withLandCoverage(
            V15TerrainDefinition definition,
            int coveragePpm) {
        return new V15TerrainDefinition(
                NormalizedValue.ofPartsPerMillion(coveragePpm),
                definition.landmassScale(),
                definition.fragmentation(),
                definition.relief(),
                definition.localRelief(),
                definition.landformScale(),
                definition.ruggedness());
    }

    private static boolean sameLandCoverage(
            V15TerrainDefinition first,
            V15TerrainDefinition second) {
        return first.landCoverage().partsPerMillion() == second.landCoverage().partsPerMillion();
    }

    private static int area(ContinuumWorldDomain domain) {
        return Math.multiplyExact(Math.toIntExact(domain.width()), Math.toIntExact(domain.height()));
    }
}
