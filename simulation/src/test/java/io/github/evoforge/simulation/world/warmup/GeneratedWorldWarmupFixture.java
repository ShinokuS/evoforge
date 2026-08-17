package io.github.evoforge.simulation.world.warmup;

import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.world.atlas.WorldAtlasGenerator;
import io.github.evoforge.simulation.world.bootstrap.GeneratedWorldBootstrap;
import io.github.evoforge.simulation.world.bootstrap.GeneratedWorldRuntime;
import io.github.evoforge.simulation.world.genesis.ClimateSpec;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.genesis.WorldSpec;
import io.github.evoforge.simulation.world.geology.CompiledGeologyProfile;
import io.github.evoforge.simulation.world.geology.GeologyGenerationStage;
import io.github.evoforge.simulation.world.geology.GeologyMaterialKey;
import io.github.evoforge.simulation.world.geology.GeologyProfileCompiler;
import io.github.evoforge.simulation.world.geology.GeologyProfileDefinition;
import io.github.evoforge.simulation.world.geology.GeologyProfileLoader;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.materialization.TerrainMaterialBindings;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import io.github.evoforge.simulation.world.terrain.generation.CompiledTerrainProfile;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialKey;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialRole;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialSetDefinition;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialSetLoader;
import io.github.evoforge.simulation.world.terrain.generation.TerrainProfileCompiler;
import io.github.evoforge.simulation.world.terrain.generation.TerrainProfileDefinition;
import io.github.evoforge.simulation.world.terrain.generation.TerrainProfileLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

final class GeneratedWorldWarmupFixture {
    private GeneratedWorldWarmupFixture() { }

    static GeneratedWorldRuntime create(long seed, ClimateSpec climate) {
        return create(seed, climate, bounds());
    }

    static GeneratedWorldRuntime create(
            long seed,
            ClimateSpec climate,
            WorldBounds bounds) {
        return create(seed, climate, bounds, true);
    }

    static GeneratedWorldRuntime createWithoutAtmosphericForcing(
            long seed,
            ClimateSpec climate,
            WorldBounds bounds) {
        return create(seed, climate, bounds, false);
    }

    private static GeneratedWorldRuntime create(
            long seed,
            ClimateSpec climate,
            WorldBounds bounds,
            boolean attachAtmosphericForcing) {
        if (bounds == null) throw new IllegalArgumentException("bounds must not be null");
        WorldGenesis genesis = WorldGenesis.current(new WorldSpec(bounds, climate), seed);
        CompiledTerrainProfile terrainProfile = terrainProfile();
        CompiledGeologyProfile geologyProfile = geologyProfile();

        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId topsoil = assembly.landscapeDefinition(
                terrainProfile.materials().require(TerrainMaterialRole.SURFACE).value(),
                1_050L);
        assembly.soilProperties(topsoil, 550_000, 100_000);

        LandscapeDefinitionId soil = assembly.landscapeDefinition(
                terrainProfile.materials().require(TerrainMaterialRole.SUBSURFACE).value(),
                1_100L);
        assembly.soilProperties(soil, 450_000, 60_000);

        LandscapeDefinitionId sand = assembly.landscapeDefinition(
                terrainProfile.materials().require(TerrainMaterialRole.SEDIMENT).value(),
                1_300L);
        assembly.soilProperties(sand, 350_000, 250_000);

        TerrainMaterialKey legacyBedrock = terrainProfile.materials().require(TerrainMaterialRole.BEDROCK);
        LandscapeDefinitionId rock = assembly.landscapeDefinition(legacyBedrock.value());

        TerrainMaterialBindings bindings = TerrainMaterialBindings.forProfile(
                terrainProfile,
                Map.of(
                        TerrainMaterialRole.SURFACE, topsoil,
                        TerrainMaterialRole.SUBSURFACE, soil,
                        TerrainMaterialRole.SEDIMENT, sand,
                        TerrainMaterialRole.BEDROCK, rock));

        Map<TerrainMaterialKey, LandscapeDefinitionId> geologyBindings = new LinkedHashMap<>();
        for (GeologyMaterialKey geologyMaterial : geologyProfile.materials().values()) {
            TerrainMaterialKey material = TerrainMaterialKey.of(geologyMaterial.value());
            LandscapeDefinitionId id = material.equals(legacyBedrock)
                    ? rock
                    : assembly.landscapeDefinition(material.value());
            geologyBindings.put(material, id);
        }
        bindings = bindings.withMaterials(geologyBindings);

        GeneratedWorldBootstrap bootstrap = new GeneratedWorldBootstrap(
                WorldAtlasGenerator.withGeology(new GeologyGenerationStage(geologyProfile)));
        if (attachAtmosphericForcing) {
            return bootstrap.create(genesis, assembly, terrainProfile, bindings);
        }
        return bootstrap.createWithoutAtmosphericForcing(
                genesis,
                assembly,
                terrainProfile,
                bindings);
    }

    static CompiledTerrainProfile terrainProfile() {
        TerrainProfileDefinition profile = new TerrainProfileLoader().load(
                asset("assets/definitions/worldgen/terrain/temperate.json"));
        TerrainMaterialSetDefinition materials = new TerrainMaterialSetLoader().load(
                asset("assets/definitions/worldgen/terrain/material-sets/temperate-ground.json"));
        return new TerrainProfileCompiler().compile(profile, materials);
    }

    static CompiledGeologyProfile geologyProfile() {
        GeologyProfileDefinition profile = new GeologyProfileLoader().load(
                asset("assets/definitions/worldgen/geology/temperate-crust.json"));
        return new GeologyProfileCompiler().compile(profile);
    }

    static WorldBounds bounds() {
        return new WorldBounds(0, 3, 0, 3, -4, 4);
    }

    private static Path asset(String relative) {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relative);
            if (Files.isRegularFile(candidate)) return candidate;
            current = current.getParent();
        }
        throw new IllegalStateException("canonical generated-world asset not found: " + relative);
    }
}
