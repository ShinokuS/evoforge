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
        StandingWaterMorphologyTopology morphology = new DenseStandingWaterMorphologyTopology(
                bounds,
                List.of(new StandingWaterMorphology(0, 1, 4L, 1)));
        StandingWaterExternalSinkCalibration calibration =
                new StandingWaterExternalSinkCalibration(1, 1, 1);
        StandingWaterExternalSinkTopology externalSinks =
                new DenseStandingWaterExternalSinkTopology(bounds, new boolean[] {true});
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
        StandingWaterExternalSinkRecipe recipe = StandingWaterExternalSinkRecipe.balanced();
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
                    calls.add("morphology");
                    return morphology;
                },
                (inputBounds, inputRecipe) -> {
                    assertSame(bounds, inputBounds);
                    assertSame(recipe, inputRecipe);
                    calls.add("calibrate");
                    return calibration;
                },
                recipe,
                (water, analyzedMorphology, analyzedCalibration) -> {
                    assertSame(selectedWater, water);
                    assertSame(morphology, analyzedMorphology);
                    assertSame(calibration, analyzedCalibration);
                    calls.add("sinks");
                    return externalSinks;
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
                (analyzedWater, analyzedSpills, analyzedSinks) -> {
                    assertSame(selectedWater, analyzedWater);
                    assertSame(spills, analyzedSpills);
                    assertSame(externalSinks, analyzedSinks);
                    calls.add("routes");
                    return routes;
                });

        StandingWaterHydrologyTopology result = stage.generate(elevation);

        assertSame(rawWater, result.rawStandingWater());
        assertSame(selectedWater, result.standingWater());
        assertSame(morphology, result.morphology());
        assertSame(externalSinks, result.externalSinks());
        assertSame(rims, result.rims());
        assertSame(spills, result.spills());
        assertSame(routes, result.boundaryRoutes());
        assertEquals(
                List.of("water", "select", "morphology", "calibrate", "sinks", "rims", "spills", "routes"),
                calls);
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
        assertEquals(0, result.externalSinks().externalSinkCount());
        assertEquals(0, result.spills().connections().size());
    }

    @Test
    void standardCompositionDoesNotTreatSmallEdgeWaterAsExternalByContactAlone() {
        WorldBounds bounds = new WorldBounds(0, 15, 0, 15, -4, 4);
        ElevationField elevation = new ElevationField() {
            @Override
            public WorldBounds bounds() {
                return bounds;
            }

            @Override
            public int elevationAt(int x, int y) {
                return x <= 1 && y <= 1 ? -1 : 1;
            }
        };

        StandingWaterHydrologyTopology result =
                StandingWaterHydrologyTopologyStage.standard().generate(elevation);

        assertEquals(1, result.bodyCount());
        assertEquals(0, result.externalSinks().externalSinkCount());
        assertEquals(true, result.boundaryRoutes().route(0).boundaryConnected());
        assertEquals(false, result.boundaryRoutes().route(0).externalSink());
        assertEquals(false, result.boundaryRoutes().route(0).reachesExternalSink());
    }

    @Test
    void stageRejectsNullDependencyAndNullOwnerOutputAtCompositionBoundary() {
        StandingWaterExternalSinkRecipe recipe = StandingWaterExternalSinkRecipe.balanced();
        assertThrows(IllegalArgumentException.class, () -> new StandingWaterHydrologyTopologyStage(
                null,
                raw -> raw,
                water -> null,
                (bounds, inputRecipe) -> null,
                recipe,
                (water, morphology, calibration) -> null,
                (e, w) -> null,
                (e, w) -> null,
                (w, s, sinks) -> null));

        ElevationField elevation = constantElevation(
                new WorldBounds(0, 0, 0, 0, -2, 2),
                1L);
        StandingWaterHydrologyTopologyStage stage = new StandingWaterHydrologyTopologyStage(
                ignored -> null,
                raw -> raw,
                water -> null,
                (bounds, inputRecipe) -> null,
                recipe,
                (water, morphology, calibration) -> null,
                (e, w) -> null,
                (e, w) -> null,
                (w, s, sinks) -> null);

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
