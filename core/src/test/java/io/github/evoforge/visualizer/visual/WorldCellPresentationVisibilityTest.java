package io.github.evoforge.visualizer.visual;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.material.MaterialDefinitionId;
import io.github.evoforge.visualizer.VisualizerState;
import org.junit.jupiter.api.Test;

final class WorldCellPresentationVisibilityTest {

    @Test
    void surfaceViewShowsStandingCellsAcrossSeveralZLevelsAtOnce() {
        SimulationAssembly assembly = SimulationAssembly.create();
        MaterialDefinitionId ground = assembly.landscapeDefinition("test:route_surface");
        assembly.placeTerrain(0, 0, -1, ground);
        assembly.placeTerrain(1, 0, 0, ground);
        assembly.placeTerrain(2, 0, 1, ground);
        SimulationRuntime runtime = assembly.start();

        VisualizerState state = new VisualizerState();
        SurfaceProjectionResolver surfaces = new SurfaceProjectionResolver(runtime.view());

        assertTrue(WorldCellPresentationVisibility.visible(state, surfaces, 0, 0, 0));
        assertTrue(WorldCellPresentationVisibility.visible(state, surfaces, 1, 0, 1));
        assertTrue(WorldCellPresentationVisibility.visible(state, surfaces, 2, 0, 2));
        assertFalse(WorldCellPresentationVisibility.visible(state, surfaces, 2, 0, 1));
    }

    @Test
    void debugSliceStillUsesExplicitSelectedZ() {
        SimulationAssembly assembly = SimulationAssembly.create();
        MaterialDefinitionId ground = assembly.landscapeDefinition("test:route_slice");
        assembly.placeTerrain(0, 0, 3, ground);
        SimulationRuntime runtime = assembly.start();

        VisualizerState state = new VisualizerState();
        state.toggleDebugSlice();
        state.setSelectedZ(2);
        SurfaceProjectionResolver surfaces = new SurfaceProjectionResolver(runtime.view());

        assertTrue(WorldCellPresentationVisibility.visible(state, surfaces, 0, 0, 2));
        assertFalse(WorldCellPresentationVisibility.visible(state, surfaces, 0, 0, 4));
    }
}
