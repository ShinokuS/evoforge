package io.github.evoforge.visualizer;

import io.github.evoforge.simulation.control.movement.MoveStepCommand;
import io.github.evoforge.simulation.control.movement.MoveStepResult;
import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.mechanics.geometry.RampShape;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;

/** Builds the deterministic Z/cave torture scene used by the visualizer. */
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
        buildMountainCaveAndRampChain(assembly, ground);
        buildFlatRoofCavern(assembly, ground);

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

        for (int x = -16; x <= 16; x++) {
            for (int y = -11; y <= 11; y++) {
                if (x == SHAFT_X && y == SHAFT_Y) {
                    continue;
                }
                assembly.placeTerrain(x, y, -1, ground);
            }
        }

        // Several genuinely empty vertical cells end at a much lower floor.
        assembly.placeTerrain(SHAFT_X, SHAFT_Y, -5, ground);
    }

    private static void buildBasePlateau(
            SimulationAssembly assembly,
            LandscapeDefinitionId ground) {

        for (int x = -7; x <= 7; x++) {
            for (int y = -5; y <= 5; y++) {
                if (x == SHAFT_X && y == SHAFT_Y) {
                    continue;
                }
                if (y == 5 && x >= 5) {
                    continue;
                }
                if (x == 7 && y >= 4) {
                    continue;
                }
                assembly.placeTerrain(x, y, 0, ground);
            }
        }
    }

    private static void buildBaseRamps(
            SimulationAssembly assembly,
            LandscapeDefinitionId ground) {

        placeRamp(assembly, ground, 0, -6, 0, RampShape.POSITIVE_Y);
        placeRamp(assembly, ground, 0, 6, 0, RampShape.NEGATIVE_Y);
        placeRamp(assembly, ground, -8, 0, 0, RampShape.POSITIVE_X);
        placeRamp(assembly, ground, 8, -3, 0, RampShape.NEGATIVE_X);
    }

    private static void buildMountainCaveAndRampChain(
            SimulationAssembly assembly,
            LandscapeDefinitionId ground) {

        // Mountain body at terrain Z=1. Missing cells form a west-facing mouth
        // and chamber; the base plateau at terrain Z=0 remains the cave floor.
        for (int x = 1; x <= 6; x++) {
            for (int y = -4; y <= 4; y++) {
                if (isMountainCaveAir(x, y)) {
                    continue;
                }
                assembly.placeTerrain(x, y, 1, ground);
            }
        }

        // Real roof above the chamber. Looking from higher slices therefore
        // sees mountain mass, not a magically illuminated cave interior.
        for (int x = 1; x <= 5; x++) {
            for (int y = -1; y <= 1; y++) {
                assembly.placeTerrain(x, y, 2, ground);
            }
        }

        // First local ascent: base plateau standing Z=1 -> mountain Z=2.
        assembly.setShape(1, -4, 1, RampShape.POSITIVE_X);

        // Higher shelf and two more local ramps create a multi-Z climb without
        // inventing a long-slope mechanic.
        for (int x = 3; x <= 6; x++) {
            for (int y = 2; y <= 4; y++) {
                assembly.placeTerrain(x, y, 2, ground);
            }
        }
        placeRamp(assembly, ground, 2, 3, 2, RampShape.POSITIVE_X);

        for (int x = 5; x <= 6; x++) {
            for (int y = 2; y <= 4; y++) {
                assembly.placeTerrain(x, y, 3, ground);
            }
        }
        placeRamp(assembly, ground, 4, 3, 3, RampShape.POSITIVE_X);
    }

    private static boolean isMountainCaveAir(
            int x,
            int y) {

        if (x == 1 && y == 0) {
            return true;
        }
        return x >= 2 && x <= 5 && y >= -1 && y <= 1;
    }

    private static void buildFlatRoofCavern(
            SimulationAssembly assembly,
            LandscapeDefinitionId ground) {

        // Separate cave under a deliberately flat cap on the west side. The
        // chamber floor is the lower meadow (terrain Z=-1), walls occupy Z=0/1,
        // and the flat roof lives at Z=2. One missing roof cell is an actual
        // vertical opening rather than an X-ray presentation exception.
        int minX = -14;
        int maxX = -9;
        int minY = -4;
        int maxY = 3;
        int openingX = -11;
        int openingY = 0;

        for (int z = 0; z <= 1; z++) {
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    if (x != minX && x != maxX
                            && y != minY && y != maxY) {
                        continue;
                    }
                    assembly.placeTerrain(x, y, z, ground);
                }
            }
        }

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                if (x == openingX && y == openingY) {
                    continue;
                }
                assembly.placeTerrain(x, y, 2, ground);
            }
        }
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
