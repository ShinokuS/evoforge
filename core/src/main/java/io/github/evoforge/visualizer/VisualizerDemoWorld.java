package io.github.evoforge.visualizer;

import io.github.evoforge.simulation.control.movement.MoveStepCommand;
import io.github.evoforge.simulation.control.movement.MoveStepResult;
import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.mechanics.geometry.RampShape;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;

/** Builds the deterministic Z-slice stress scene used by the visualizer. */
public final class VisualizerDemoWorld {

    private static final int SHAFT_X = -4;
    private static final int SHAFT_Y = 2;

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

        buildLowerMeadowAndDeepShaft(assembly, ground);
        buildBasePlateau(assembly, ground);
        buildBaseRamps(assembly, ground);
        buildMountainWithCave(assembly, ground);
        buildHigherTiersAndRampChain(assembly, ground);

        ObjectId slow = assembly.createObject(slowWalker);
        ObjectId fast = assembly.createObject(fastWalker);
        assembly.placeObject(slow, -3, -1, 1);
        assembly.placeObject(fast, -3, 0, 1);

        SimulationRuntime runtime = assembly.start();

        requireStarted(
                runtime.submit(new MoveStepCommand(
                        slow,
                        -2,
                        -1,
                        1)),
                "slow walker");
        requireStarted(
                runtime.submit(new MoveStepCommand(
                        fast,
                        -2,
                        0,
                        1)),
                "fast walker");

        return runtime;
    }

    private static void buildLowerMeadowAndDeepShaft(
            SimulationAssembly assembly,
            LandscapeDefinitionId ground) {

        for (int x = -14; x <= 14; x++) {
            for (int y = -10; y <= 10; y++) {
                if (x == SHAFT_X && y == SHAFT_Y) {
                    continue;
                }
                assembly.placeTerrain(x, y, -1, ground);
            }
        }

        // The column above this floor is deliberately empty at Z=-2 and Z=-1.
        // With the plateau floor also absent, higher selected slices can see
        // several elevations down through one real open shaft.
        assembly.placeTerrain(SHAFT_X, SHAFT_Y, -3, ground);
    }

    private static void buildBasePlateau(
            SimulationAssembly assembly,
            LandscapeDefinitionId ground) {

        for (int x = -6; x <= 6; x++) {
            for (int y = -4; y <= 4; y++) {
                if (x == SHAFT_X && y == SHAFT_Y) {
                    continue;
                }

                // Irregular north-east corner exercises inner/outer autotile
                // combinations instead of presenting only rectangles.
                if (y == 4 && x >= 4) {
                    continue;
                }
                if (x == 6 && y >= 3) {
                    continue;
                }

                assembly.placeTerrain(x, y, 0, ground);
            }
        }
    }

    private static void buildBaseRamps(
            SimulationAssembly assembly,
            LandscapeDefinitionId ground) {

        placeRamp(assembly, ground, 0, -5, 0, RampShape.POSITIVE_Y);
        placeRamp(assembly, ground, 0, 5, 0, RampShape.NEGATIVE_Y);
        placeRamp(assembly, ground, -7, 0, 0, RampShape.POSITIVE_X);

        // Keep the east-side -X sample away from the mountain body so its
        // upper landing remains a real free standing-Z=1 surface.
        placeRamp(assembly, ground, 7, -4, 0, RampShape.NEGATIVE_X);
    }

    private static void buildMountainWithCave(
            SimulationAssembly assembly,
            LandscapeDefinitionId ground) {

        // Terrain at Z=1 becomes solid mountain body when the selected
        // standing slice is Z=1, and becomes its walkable top at Z=2.
        for (int x = 1; x <= 6; x++) {
            for (int y = -3; y <= 3; y++) {
                if (isCaveVoid(x, y)) {
                    continue;
                }
                assembly.placeTerrain(x, y, 1, ground);
            }
        }

        // West-facing entrance plus a chamber are simply absent body cells at
        // Z=1. The existing plateau terrain at Z=0 remains the cave floor.
        // One Ramp on the south-west shoulder reaches the mountain top.
        assembly.setShape(1, -3, 1, RampShape.POSITIVE_X);
    }

    private static boolean isCaveVoid(
            int x,
            int y) {

        if (x == 1 && y == 0) {
            return true;
        }
        return x >= 2 && x <= 5 && y >= -1 && y <= 1;
    }

    private static void buildHigherTiersAndRampChain(
            SimulationAssembly assembly,
            LandscapeDefinitionId ground) {

        // Third standing elevation. Part of this footprint lies over the cave,
        // so switching slices demonstrates a roof without ghosting upper floors.
        for (int x = 3; x <= 6; x++) {
            for (int y = 0; y <= 3; y++) {
                assembly.placeTerrain(x, y, 2, ground);
            }
        }

        // Z=2 -> Z=3 transition on the west shoulder of the third tier.
        placeRamp(assembly, ground, 2, 2, 2, RampShape.POSITIVE_X);

        // Small summit body at terrain Z=3. The ramp at (4,2,3) follows the
        // previous +X ascent and proves that successive local ramps can form a
        // longer mountain climb without a special long-slope mechanic.
        assembly.placeTerrain(5, 1, 3, ground);
        assembly.placeTerrain(6, 1, 3, ground);
        assembly.placeTerrain(5, 2, 3, ground);
        assembly.placeTerrain(6, 2, 3, ground);
        assembly.placeTerrain(5, 3, 3, ground);
        assembly.placeTerrain(6, 3, 3, ground);
        placeRamp(assembly, ground, 4, 2, 3, RampShape.POSITIVE_X);
    }

    private static void placeRamp(
            SimulationAssembly assembly,
            LandscapeDefinitionId ground,
            int x,
            int y,
            int terrainZ,
            RampShape ramp) {

        assembly.placeTerrain(x, y, terrainZ, ground);
        assembly.setShape(x, y, terrainZ, ramp);
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
