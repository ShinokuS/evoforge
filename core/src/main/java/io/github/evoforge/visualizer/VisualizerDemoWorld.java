package io.github.evoforge.visualizer;

import io.github.evoforge.simulation.control.movement.MoveStepCommand;
import io.github.evoforge.simulation.control.movement.MoveStepResult;
import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.mechanics.geometry.RampShape;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;

/** Builds the deterministic terrain/readability scene used by the visualizer. */
public final class VisualizerDemoWorld {

    private VisualizerDemoWorld() {
    }

    public static SimulationRuntime create() {
        SimulationAssembly assembly = SimulationAssembly.create();

        LandscapeDefinitionId ground = assembly.landscapeDefinition(
                "demo:ground");
        ObjectDefinitionId slowWalker = assembly.objectDefinition(
                "demo:slow_walker");
        ObjectDefinitionId fastWalker = assembly.objectDefinition(
                "demo:fast_walker");

        assembly.movementRate(slowWalker, 125);
        assembly.movementRate(fastWalker, 500);

        buildLowerMeadow(assembly, ground);
        buildMainPlateau(assembly, ground);
        buildMainRamps(assembly, ground);
        buildUpperTerrace(assembly, ground);

        ObjectId slow = assembly.createObject(slowWalker);
        ObjectId fast = assembly.createObject(fastWalker);
        assembly.placeObject(slow, -2, -1, 1);
        assembly.placeObject(fast, -2, 0, 1);

        SimulationRuntime runtime = assembly.start();

        requireStarted(
                runtime.submit(new MoveStepCommand(
                        slow,
                        -1,
                        -1,
                        1)),
                "slow walker");
        requireStarted(
                runtime.submit(new MoveStepCommand(
                        fast,
                        -1,
                        0,
                        1)),
                "fast walker");

        return runtime;
    }

    private static void buildLowerMeadow(
            SimulationAssembly assembly,
            LandscapeDefinitionId ground) {

        for (int x = -12; x <= 12; x++) {
            for (int y = -9; y <= 9; y++) {
                assembly.placeTerrain(x, y, -1, ground);
            }
        }
    }

    private static void buildMainPlateau(
            SimulationAssembly assembly,
            LandscapeDefinitionId ground) {

        for (int x = -4; x <= 4; x++) {
            for (int y = -3; y <= 3; y++) {
                assembly.placeTerrain(x, y, 0, ground);
            }
        }

        // Cut two cells from one corner so inner/outer edge combinations are
        // visible in the same acceptance scene.
        // The lower meadow remains authoritative beneath those holes.
        assembly.removeTerrain(4, 3, 0);
        assembly.removeTerrain(3, 3, 0);
    }

    private static void buildMainRamps(
            SimulationAssembly assembly,
            LandscapeDefinitionId ground) {

        // South side: rise toward +Y into the plateau.
        assembly.placeTerrain(0, -4, 0, ground);
        assembly.setShape(0, -4, 0, RampShape.POSITIVE_Y);

        // North side: rise toward -Y into the plateau.
        assembly.placeTerrain(0, 4, 0, ground);
        assembly.setShape(0, 4, 0, RampShape.NEGATIVE_Y);

        // West side: rise toward +X into the plateau.
        assembly.placeTerrain(-5, 0, 0, ground);
        assembly.setShape(-5, 0, 0, RampShape.POSITIVE_X);

        // East side: rise toward -X into the plateau.
        assembly.placeTerrain(5, 0, 0, ground);
        assembly.setShape(5, 0, 0, RampShape.NEGATIVE_X);
    }

    private static void buildUpperTerrace(
            SimulationAssembly assembly,
            LandscapeDefinitionId ground) {

        // Small second elevation used to verify repeated Z slicing rather than
        // special-casing a single plateau height.
        for (int x = 1; x <= 3; x++) {
            for (int y = 1; y <= 2; y++) {
                assembly.placeTerrain(x, y, 1, ground);
            }
        }

        // A second-level +X ramp connects standing Z=1 to standing Z=2.
        assembly.placeTerrain(0, 2, 1, ground);
        assembly.setShape(0, 2, 1, RampShape.POSITIVE_X);
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
