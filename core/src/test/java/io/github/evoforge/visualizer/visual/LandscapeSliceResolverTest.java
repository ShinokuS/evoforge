package io.github.evoforge.visualizer.visual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.mechanics.geometry.RampShape;
import org.junit.jupiter.api.Test;

final class LandscapeSliceResolverTest {

    @Test
    void solidBodyTakesPriorityOverSupportBelow() {
        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId ground = assembly.landscapeDefinition("test:ground");

        assembly.placeTerrain(0, 0, -1, ground);
        assembly.placeTerrain(0, 0, 0, ground);

        LandscapeSliceResolver resolver = resolver(assembly.start());
        LandscapeSliceResolver.Cell cell = resolver.resolve(0, 0, 0, 4);

        assertEquals(LandscapeSliceResolver.Kind.SOLID_BODY, cell.kind());
        assertEquals(0, cell.terrainZ());
        assertEquals(0, cell.lowerDepth());
    }

    @Test
    void supportImmediatelyBelowIsCurrentSurface() {
        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId ground = assembly.landscapeDefinition("test:ground");
        assembly.placeTerrain(0, 0, -1, ground);

        LandscapeSliceResolver.Cell cell = resolver(assembly.start())
                .resolve(0, 0, 0, 4);

        assertEquals(LandscapeSliceResolver.Kind.CURRENT_SURFACE, cell.kind());
        assertEquals(-1, cell.terrainZ());
        assertEquals(0, cell.lowerDepth());
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
        assertEquals(2, visible.lowerDepth());

        LandscapeSliceResolver.Cell clipped = resolver.resolve(0, 0, 0, 1);
        assertEquals(LandscapeSliceResolver.Kind.EMPTY, clipped.kind());
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

    private static LandscapeSliceResolver resolver(
            SimulationRuntime runtime) {

        return new LandscapeSliceResolver(runtime.view());
    }
}
