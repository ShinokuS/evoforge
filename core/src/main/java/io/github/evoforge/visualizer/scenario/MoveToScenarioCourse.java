package io.github.evoforge.visualizer.scenario;

import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.mechanics.geometry.RampShape;

/** Shared multi-level terrain used by focused MoveTo visualizer scenarios. */
final class MoveToScenarioCourse {

    static final int MIN_STANDING_Z = 0;
    static final int MAX_STANDING_Z = 4;

    private static final int MIN_Y = -3;
    private static final int MAX_Y = 3;
    private static final int RAMP_SPACING = 4;

    private MoveToScenarioCourse() {
    }

    static void build(
            SimulationAssembly assembly,
            LandscapeDefinitionId ground) {

        ScenarioTerrain.fill(
                assembly,
                ground,
                -5,
                -1,
                MIN_Y,
                MAX_Y,
                -1);

        for (int level = MIN_STANDING_Z;
                level < MAX_STANDING_Z;
                level++) {

            int rampX = level * RAMP_SPACING;
            ScenarioTerrain.placeRamp(
                    assembly,
                    ground,
                    rampX,
                    0,
                    level,
                    RampShape.POSITIVE_X);
            ScenarioTerrain.fill(
                    assembly,
                    ground,
                    rampX + 1,
                    rampX + RAMP_SPACING - 1,
                    MIN_Y,
                    MAX_Y,
                    level);
        }
    }
}
