package io.github.evoforge.visualizer.visual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.mechanics.geometry.RampShape;
import org.junit.jupiter.api.Test;

final class LandscapeSliceResolverTest {

    @Test
    void solidBodyTakesPriorityAndReportsProgressiveBodyDepth() {
        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId ground = assembly.landscapeDefinition("test:ground");

        assembly.placeTerrain(0, 0, -1, ground);
        assembly.placeTerrain(0, 0, 0, ground);
        assembly.placeTerrain(0, 0, 1, ground);
        assembly.placeTerrain(0, 0, 2, ground);

        LandscapeSliceResolver.Cell cell = resolver(assembly.start())
                .resolve(0, 0, 0, 4);

        assertEquals(LandscapeSliceResolver.Kind.SOLID_BODY, cell.kind());
        assertEquals(0, cell.terrainZ());
        assertEquals(3, cell.bodyDepth());
        assertEquals(0, cell.dropDepth());
    }

    @Test
    void currentSurfaceQueryMatchesSliceRoleWithoutExposureAnalysis() {
        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId ground = assembly.landscapeDefinition("test:ground");
        assembly.placeTerrain(0, 0, 0, ground);

        LandscapeSliceResolver resolver = resolver(assembly.start());

        assertTrue(resolver.isCurrentSurface(0, 0, 1));
        assertFalse(resolver.isCurrentSurface(0, 0, 0));
        assertFalse(resolver.isCurrentSurface(1, 0, 1));
        assertFalse(resolver.isCurrentSurface(0, 0, Integer.MIN_VALUE));
    }

    @Test
    void openSurfaceHasNoCoverAndZeroExposureDistance() {
        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId ground = assembly.landscapeDefinition("test:ground");
        assembly.placeTerrain(0, 0, -1, ground);

        LandscapeSliceResolver.Analysis analysis = resolver(assembly.start())
                .analyze(-1, 1, -1, 1, 0, 4, 4);
        LandscapeSliceResolver.Cell cell = analysis.resolve(0, 0);

        assertEquals(LandscapeSliceResolver.Kind.CURRENT_SURFACE, cell.kind());
        assertEquals(-1, cell.terrainZ());
        assertEquals(0, cell.coverDepth());
        assertEquals(0, cell.ceilingDistance());
        assertEquals(0, cell.exposureDistance());
    }

    @Test
    void nearestLowerSurfaceIsVisibleOnlyThroughOpenColumn() {
        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId ground = assembly.landscapeDefinition("test:ground");
        assembly.placeTerrain(0, 0, -3, ground);

        LandscapeSliceResolver resolver = resolver(assembly.start());

        LandscapeSliceResolver.Cell visible = resolver.resolve(0, 0, 0, 2);
        assertEquals(LandscapeSliceResolver.Kind.LOWER_SURFACE, visible.kind());
        assertEquals(-3, visible.terrainZ());
        assertEquals(2, visible.dropDepth());

        LandscapeSliceResolver.Cell clipped = resolver.resolve(0, 0, 0, 1);
        assertEquals(LandscapeSliceResolver.Kind.EMPTY, clipped.kind());
    }

    @Test
    void caveExposureFallsWithRealAirDistanceFromMouth() {
        SimulationRuntime runtime = caveRoom(true, 2);
        LandscapeSliceResolver.Analysis analysis = resolver(runtime)
                .analyze(-1, 4, 0, 4, 1, 0, 6);

        LandscapeSliceResolver.Cell mouth = analysis.resolve(0, 2);
        LandscapeSliceResolver.Cell near = analysis.resolve(1, 2);
        LandscapeSliceResolver.Cell deep = analysis.resolve(3, 2);

        assertEquals(1, mouth.coverDepth());
        assertEquals(1, mouth.ceilingDistance());
        assertEquals(1, mouth.exposureDistance());
        assertEquals(2, near.exposureDistance());
        assertEquals(4, deep.exposureDistance());
    }

    @Test
    void enclosedRoomSaturatesExposureInsteadOfPretendingItIsOutside() {
        SimulationRuntime runtime = caveRoom(false, 2);
        LandscapeSliceResolver.Analysis analysis = resolver(runtime)
                .analyze(0, 4, 0, 4, 1, 0, 4);

        LandscapeSliceResolver.Cell center = analysis.resolve(2, 2);

        assertEquals(1, center.coverDepth());
        assertEquals(5, center.exposureDistance());
    }

    @Test
    void tallCavernReportsCeilingDistanceIndependentlyOfExposure() {
        SimulationRuntime runtime = caveRoom(true, 5);
        LandscapeSliceResolver.Analysis analysis = resolver(runtime)
                .analyze(-1, 4, 0, 4, 1, 0, 8);

        LandscapeSliceResolver.Cell center = analysis.resolve(2, 2);

        assertEquals(4, center.ceilingDistance());
        assertEquals(1, center.coverDepth());
        assertEquals(3, center.exposureDistance());
    }

    @Test
    void genericOpaqueVolumeCanBlockViewWithoutBecomingTerrain() {
        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId ground = assembly.landscapeDefinition("test:ground");
        assembly.placeTerrain(0, 0, -3, ground);
        SimulationRuntime runtime = assembly.start();

        VisibilityVolumeLookup terrain = new TerrainVisibilityVolume(runtime.view());
        VisibilityVolumeLookup withFutureOccluder = new VisibilityVolumeLookup() {
            @Override
            public boolean solid(int x, int y, int z) {
                return terrain.solid(x, y, z)
                        || x == 0 && y == 0 && z == -1;
            }

            @Override
            public boolean opaque(int x, int y, int z) {
                return terrain.opaque(x, y, z)
                        || x == 0 && y == 0 && z == -1;
            }

            @Override
            public boolean empty() {
                return false;
            }

            @Override
            public int minOccupiedZ() {
                return -3;
            }

            @Override
            public int maxOccupiedZ() {
                return -1;
            }
        };

        LandscapeSliceResolver resolver = new LandscapeSliceResolver(
                runtime.view(),
                withFutureOccluder);

        assertEquals(
                LandscapeSliceResolver.Kind.EMPTY,
                resolver.resolve(0, 0, 0, 3).kind());
    }

    @Test
    void slicePreservesConcreteRampShapeForPresentation() {
        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId ground = assembly.landscapeDefinition("test:ground");
        assembly.placeTerrain(0, 0, 0, ground);
        assembly.setShape(0, 0, 0, RampShape.POSITIVE_X);

        LandscapeSliceResolver.Cell cell = resolver(assembly.start())
                .resolve(0, 0, 0, 0);

        assertEquals(LandscapeSliceResolver.Kind.SOLID_BODY, cell.kind());
        assertSame(RampShape.POSITIVE_X, cell.shape());
    }

    private static SimulationRuntime caveRoom(
            boolean entrance,
            int roofZ) {

        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId ground = assembly.landscapeDefinition("test:ground");

        for (int x = 0; x <= 4; x++) {
            for (int y = 0; y <= 4; y++) {
                assembly.placeTerrain(x, y, 0, ground);
                assembly.placeTerrain(x, y, roofZ, ground);
            }
        }

        for (int z = 1; z < roofZ; z++) {
            for (int x = 0; x <= 4; x++) {
                for (int y = 0; y <= 4; y++) {
                    boolean boundary = x == 0 || x == 4 || y == 0 || y == 4;
                    boolean mouth = entrance && x == 0 && y == 2;
                    if (boundary && !mouth) {
                        assembly.placeTerrain(x, y, z, ground);
                    }
                }
            }
        }

        return assembly.start();
    }

    private static LandscapeSliceResolver resolver(
            SimulationRuntime runtime) {

        return new LandscapeSliceResolver(runtime.view());
    }
}
