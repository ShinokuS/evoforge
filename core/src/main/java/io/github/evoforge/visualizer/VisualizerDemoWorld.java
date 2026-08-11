package io.github.evoforge.visualizer;

import io.github.evoforge.simulation.control.movement.MoveStepCommand;
import io.github.evoforge.simulation.control.movement.MoveStepResult;
import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.mechanics.geometry.RampShape;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;

/** Builds the small deterministic world used only by the debug visualizer. */
public final class VisualizerDemoWorld {

    private VisualizerDemoWorld() {
    }

    public static SimulationRuntime create() {
        SimulationAssembly assembly = SimulationAssembly.create();

        LandscapeDefinitionId ground =
                assembly.landscapeDefinition("demo:ground");

        ObjectDefinitionId slowWalker =
                assembly.objectDefinition("demo:slow_walker");
        ObjectDefinitionId fastWalker =
                assembly.objectDefinition("demo:fast_walker");

        assembly.movementRate(slowWalker, 125);
        assembly.movementRate(fastWalker, 500);

        buildFlatPlatform(
                assembly,
                ground);
        buildRampSample(
                assembly,
                ground);

        ObjectId slow = assembly.createObject(slowWalker);
        ObjectId fast = assembly.createObject(fastWalker);

        assembly.placeObject(slow, -4, 1, 0);
        assembly.placeObject(fast, -4, -1, 0);

        SimulationRuntime runtime = assembly.start();

        requireStarted(
                runtime.submit(
                        new MoveStepCommand(
                                slow,
                                -3,
                                1,
                                0)),
                "slow walker");
        requireStarted(
                runtime.submit(
                        new MoveStepCommand(
                                fast,
                                -3,
                                -1,
                                0)),
                "fast walker");

        return runtime;
    }

    private static void buildFlatPlatform(
            SimulationAssembly assembly,
            LandscapeDefinitionId ground) {

        for (int x = -6; x <= 1; x++) {
            for (int y = -3; y <= 3; y++) {
                assembly.placeTerrain(
                        x,
                        y,
                        -1,
                        ground);
            }
        }
    }

    private static void buildRampSample(
            SimulationAssembly assembly,
            LandscapeDefinitionId ground) {

        // Standing Z -1, supported by terrain Z -2.
        assembly.placeTerrain(
                4,
                0,
                -2,
                ground);

        // Ramp anchor Z -1 supports its navigation position on standing Z 0.
        assembly.placeTerrain(
                4,
                1,
                -1,
                ground);
        assembly.setShape(
                4,
                1,
                -1,
                RampShape.POSITIVE_Y);

        // Upper standing position Z 0, supported by ordinary terrain Z -1.
        assembly.placeTerrain(
                4,
                2,
                -1,
                ground);
    }

    private static void requireStarted(
            MoveStepResult result,
            String label) {

        if (result != MoveStepResult.STARTED) {
            throw new IllegalStateException(
                    label + " demo movement was rejected: " + result);
        }
    }
}
