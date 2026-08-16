package io.github.evoforge.simulation.world.warmup;

import java.nio.file.Files;
import java.nio.file.Path;

import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.world.bootstrap.GeneratedWorldBootstrap;
import io.github.evoforge.simulation.world.bootstrap.GeneratedWorldRuntime;
import io.github.evoforge.simulation.world.genesis.HydroClimateSpec;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.genesis.WorldSpec;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.materialization.TerrainMaterialBindings;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import io.github.evoforge.simulation.world.terrain.generation.TerrainPalette;
import io.github.evoforge.simulation.world.terrain.generation.TerrainPaletteLoader;

final class GeneratedWorldWarmupFixture {

    private GeneratedWorldWarmupFixture() {
    }

    static GeneratedWorldRuntime create(
            long seed,
            HydroClimateSpec climate) {
        return create(seed, climate, bounds());
    }

    static GeneratedWorldRuntime create(
            long seed,
            HydroClimateSpec climate,
            WorldBounds bounds) {
        if (bounds == null) {
            throw new IllegalArgumentException("bounds must not be null");
        }
        WorldGenesis genesis = WorldGenesis.current(
                new WorldSpec(bounds, climate),
                seed);
        TerrainPalette palette = new TerrainPaletteLoader().load(
                canonicalPalette());

        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId topsoil = assembly.landscapeDefinition(
                palette.materials().topsoil().value(),
                1_050L);
        assembly.soilProperties(topsoil, 550_000, 100_000);

        LandscapeDefinitionId soil = assembly.landscapeDefinition(
                palette.materials().soil().value(),
                1_100L);
        assembly.soilProperties(soil, 450_000, 60_000);

        LandscapeDefinitionId sand = assembly.landscapeDefinition(
                palette.materials().sand().value(),
                1_300L);
        assembly.soilProperties(sand, 350_000, 250_000);

        LandscapeDefinitionId rock = assembly.landscapeDefinition(
                palette.materials().rock().value());

        TerrainMaterialBindings bindings = TerrainMaterialBindings.forPalette(
                palette,
                topsoil,
                soil,
                sand,
                rock);

        return new GeneratedWorldBootstrap().create(
                genesis,
                assembly,
                palette,
                bindings);
    }

    static WorldBounds bounds() {
        return new WorldBounds(0, 3, 0, 3, -4, 4);
    }

    private static Path canonicalPalette() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(
                    "assets/definitions/worldgen/terrain/temperate.json");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("canonical terrain palette not found");
    }
}
