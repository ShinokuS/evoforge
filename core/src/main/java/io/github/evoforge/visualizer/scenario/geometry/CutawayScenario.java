package io.github.evoforge.visualizer.scenario.geometry;

import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.world.material.MaterialDefinitionId;
import io.github.evoforge.visualizer.scenario.ScenarioSession;
import io.github.evoforge.visualizer.scenario.ScenarioView;
import io.github.evoforge.visualizer.scenario.VisualizerScenario;

/** Geometry-only scene for inspecting horizontal cuts, caves and deep shafts. */
public final class CutawayScenario implements VisualizerScenario {

    private static final int SHAFT_X = -4;
    private static final int SHAFT_Y = 2;

    @Override
    public String id() { return "cutaway"; }

    @Override
    public String title() { return "Z-Level / Cutaway"; }

    @Override
    public String description() {
        return "Caves, roofs, lower surfaces and a deep shaft without moving objects.";
    }

    @Override
    public ScenarioSession create() {
        SimulationAssembly assembly = SimulationAssembly.create();
        MaterialDefinitionId ground = assembly.landscapeDefinition(
                "scenario:cutaway_ground");

        buildLowerMeadow(assembly, ground);
        buildPlateau(assembly, ground);
        buildMountainCave(assembly, ground);
        buildFlatRoofCavern(assembly, ground);

        return new ScenarioSession(
                assembly.start(),
                new ScenarioView(1, -2f, 0f, 1.15f));
    }

    private static void buildLowerMeadow(
            SimulationAssembly assembly,
            MaterialDefinitionId ground) {
        for (int x = -16; x <= 16; x++) {
            for (int y = -11; y <= 11; y++) {
                if (x == SHAFT_X && y == SHAFT_Y) continue;
                assembly.placeTerrain(x, y, -1, ground);
            }
        }
        assembly.placeTerrain(SHAFT_X, SHAFT_Y, -5, ground);
    }

    private static void buildPlateau(
            SimulationAssembly assembly,
            MaterialDefinitionId ground) {
        for (int x = -7; x <= 7; x++) {
            for (int y = -5; y <= 5; y++) {
                if (x == SHAFT_X && y == SHAFT_Y) continue;
                assembly.placeTerrain(x, y, 0, ground);
            }
        }
    }

    private static void buildMountainCave(
            SimulationAssembly assembly,
            MaterialDefinitionId ground) {
        for (int x = 1; x <= 6; x++) {
            for (int y = -4; y <= 4; y++) {
                if (!isMountainCaveAir(x, y)) {
                    assembly.placeTerrain(x, y, 1, ground);
                }
            }
        }
        for (int x = 1; x <= 5; x++) {
            for (int y = -1; y <= 1; y++) {
                assembly.placeTerrain(x, y, 2, ground);
            }
        }
        for (int x = 3; x <= 6; x++) {
            for (int y = 2; y <= 4; y++) {
                assembly.placeTerrain(x, y, 2, ground);
            }
        }
        for (int x = 5; x <= 6; x++) {
            for (int y = 2; y <= 4; y++) {
                assembly.placeTerrain(x, y, 3, ground);
            }
        }
    }

    private static boolean isMountainCaveAir(int x, int y) {
        return (x == 1 && y == 0)
                || (x >= 2 && x <= 5 && y >= -1 && y <= 1);
    }

    private static void buildFlatRoofCavern(
            SimulationAssembly assembly,
            MaterialDefinitionId ground) {
        int minX = -14;
        int maxX = -9;
        int minY = -4;
        int maxY = 3;
        int openingX = -11;
        int openingY = 0;

        for (int z = 0; z <= 1; z++) {
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    if (x == minX || x == maxX || y == minY || y == maxY) {
                        assembly.placeTerrain(x, y, z, ground);
                    }
                }
            }
        }
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                if (x != openingX || y != openingY) {
                    assembly.placeTerrain(x, y, 2, ground);
                }
            }
        }
    }
}
