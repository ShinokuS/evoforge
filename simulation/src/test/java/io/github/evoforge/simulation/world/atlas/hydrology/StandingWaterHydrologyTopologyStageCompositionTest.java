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
        StandingWaterBody body = new StandingWaterBody(0, 1L, 0L, true, 0, 0, 0, 0);
        StandingWaterTopology water = new DenseStandingWaterTopology(bounds, new int[] {0}, List.of(body));
        StandingWaterRimTopology rims = new DenseStandingWaterRimTopology(bounds, List.of(List.of()));
        StandingWaterSpillTopology spills = new DenseStandingWaterSpillTopology(bounds, 1, List.of());
        StandingWaterBoundaryRouteTopology routes = new DenseStandingWaterBoundaryRouteTopology(
                bounds,
                List.of(new StandingWaterBoundaryRoute(
                        0,
                        true,
                        OptionalInt.empty(),
                        OptionalLong.of(0L))));
        List<String> calls = new ArrayList<>();

        StandingWaterHydrologyTopologyStage stage = new StandingWaterHydrologyTopologyStage(
                input -> {
                    assertSame(elevation, input);
                    calls.add("water");
                    return water;
                },
                (input, analyzedWater) -> {
                    assertSame(elevation, input);
                    assertSame(water, analyzedWater);
                    calls.add("rims");
                    return rims;
                },
                (input, analyzedWater) -> {
                    assertSame(elevation, input);
                    assertSame(water, analyzedWater);
                    calls.add("spills");
                    return spills;
                },
                (analyzedWater, analyzedSpills) -> {
                    assertSame(water, analyzedWater);
                    assertSame(spills, analyzedSpills);
                    calls.add("routes");
                    return routes;
                });

        StandingWaterHydrologyTopology result = stage.generate(elevation);

        assertSame(water, result.standingWater());
        assertSame(rims, result.rims());
        assertSame(spills, result.spills());
        assertSame(routes, result.boundaryRoutes());
        assertEquals(List.of("water", "rims", "spills", "routes"), calls);
    }

    @Test
    void standardCompositionProducesOneConsistentFactDomain() {
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
        assertEquals(1, result.bodyCount());
        assertEquals(4, result.rims().rimCells(0).size());
        assertEquals(0, result.spills().connections().size());
        assertEquals(false, result.boundaryRoutes().route(0).reachesBoundaryWater());
    }

    @Test
    void stageRejectsNullDependencyAndNullOwnerOutputAtCompositionBoundary() {
        assertThrows(IllegalArgumentException.class, () -> new StandingWaterHydrologyTopologyStage(
                null,
                (e, w) -> null,
                (e, w) -> null,
                (w, s) -> null));

        ElevationField elevation = constantElevation(
                new WorldBounds(0, 0, 0, 0, -2, 2),
                1L);
        StandingWaterHydrologyTopologyStage stage = new StandingWaterHydrologyTopologyStage(
                ignored -> null,
                (e, w) -> null,
                (e, w) -> null,
                (w, s) -> null);

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
