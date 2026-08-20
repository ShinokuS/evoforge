package io.github.evoforge.simulation.world.atlas.hydrology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

final class StandingWaterHydrologyTopologyStageCompositionTest {

    @Test
    void stageComposesReplaceableOwnersWithoutReimplementingTheirPolicy() {
        WorldBounds bounds = new WorldBounds(0, 0, 0, 0, -2, 2);
        ElevationField elevation = constantElevation(bounds, -1L);
        StandingWaterBody rawBody = new StandingWaterBody(0, 1L, 0L, true, 0, 0, 0, 0);
        StandingWaterTopology rawWater = new DenseStandingWaterTopology(bounds, new int[] {0}, List.of(rawBody));
        StandingWaterBody selectedBody = new StandingWaterBody(0, 1L, 0L, true, 0, 0, 0, 0);
        StandingWaterTopology selectedWater =
                new DenseStandingWaterTopology(bounds, new int[] {0}, List.of(selectedBody));
        StandingWaterDomainTopology domains = new DenseStandingWaterDomainTopology(
                bounds,
                List.of(StandingWaterDomainRole.OCEANIC));
        StandingWaterRimTopology rims = new DenseStandingWaterRimTopology(bounds, List.of(List.of()));
        StandingWaterSpillTopology spills = new DenseStandingWaterSpillTopology(bounds, 1, List.of());
        StandingWaterBoundaryRouteTopology routes = new DenseStandingWaterBoundaryRouteTopology(
                bounds,
                List.of(new StandingWaterBoundaryRoute(
                        0,
                        true,
                        true,
                        OptionalInt.empty(),
                        OptionalLong.of(0L))));
        List<String> calls = new ArrayList<>();

        StandingWaterHydrologyTopologyStage stage = new StandingWaterHydrologyTopologyStage(
                input -> {
                    assertSame(elevation, input);
                    calls.add("water");
                    return rawWater;
                },
                raw -> {
                    assertSame(rawWater, raw);
                    calls.add("select");
                    return selectedWater;
                },
                water -> {
                    assertSame(selectedWater, water);
                    calls.add("domains");
                    return domains;
                },
                (input, analyzedWater) -> {
                    assertSame(elevation, input);
                    assertSame(selectedWater, analyzedWater);
                    calls.add("rims");
                    return rims;
                },
                (input, analyzedWater) -> {
                    assertSame(elevation, input);
                    assertSame(selectedWater, analyzedWater);
                    calls.add("spills");
                    return spills;
                },
                (analyzedWater, analyzedSpills, analyzedDomains) -> {
                    assertSame(selectedWater, analyzedWater);
                    assertSame(spills, analyzedSpills);
                    assertSame(domains, analyzedDomains);
                    calls.add("routes");
                    return routes;
                });

        StandingWaterHydrologyTopology result = stage.generate(elevation);

        assertSame(rawWater, result.rawStandingWater());
        assertSame(selectedWater, result.standingWater());
        assertSame(domains, result.domains());
        assertSame(rims, result.rims());
        assertSame(spills, result.spills());
        assertSame(routes, result.boundaryRoutes());
        assertEquals(List.of("water", "select", "domains", "rims", "spills", "routes"), calls);
    }

    @Test
    void standardCompositionKeepsMicroWaterRawButOutOfHydrologicBodyDomain() {
        WorldBounds bounds = new WorldBounds(0, 2, 0, 2, -4, 4);
        ElevationField elevation = new ElevationField() {
            @Override
            public WorldBounds bounds() {
                return bounds;
            }

            @Override
            public int elevationAt(int x, int y) {
                return x == 1 && y == 1 ? -1 : 1;
            }
        };

        StandingWaterHydrologyTopology result =
                StandingWaterHydrologyTopologyStage.standard().generate(elevation);

        assertEquals(bounds, result.bounds());
        assertEquals(1, result.rawBodyCount());
        assertEquals(0, result.bodyCount());
        assertEquals(0, result.domains().oceanicBodyCount());
        assertEquals(0, result.spills().connections().size());
    }

    @Test
    void stageRejectsNullDependencyAndNullOwnerOutputAtCompositionBoundary() {
        assertThrows(IllegalArgumentException.class, () -> new StandingWaterHydrologyTopologyStage(
                null,
                raw -> raw,
                water -> null,
                (e, w) -> null,
                (e, w) -> null,
                (w, s, domains) -> null));

        ElevationField elevation = constantElevation(
                new WorldBounds(0, 0, 0, 0, -2, 2),
                1L);
        StandingWaterHydrologyTopologyStage stage = new StandingWaterHydrologyTopologyStage(
                ignored -> null,
                raw -> raw,
                water -> null,
                (e, w) -> null,
                (e, w) -> null,
                (w, s, domains) -> null);

        assertThrows(IllegalStateException.class, () -> stage.generate(elevation));
    }

    private static ElevationField constantElevation(WorldBounds bounds, long valueSubunits) {
        return new ElevationField() {
            @Override
            public WorldBounds bounds() {
                return bounds;
            }

            @Override
            public int elevationAt(int x, int y) {
                return Math.toIntExact(Math.floorDiv(valueSubunits, SUBUNITS_PER_CELL));
            }

            @Override
            public long elevationSubunitsAt(int x, int y) {
                return valueSubunits;
            }
        };
    }
}
