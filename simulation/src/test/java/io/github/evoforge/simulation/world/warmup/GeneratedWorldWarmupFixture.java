package io.github.evoforge.simulation.world.warmup;

import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.time.SimulationTimeScale;
import io.github.evoforge.simulation.world.atlas.WorldAtlasGenerator;
import io.github.evoforge.simulation.world.bootstrap.AtmosphericForcingPolicy;
import io.github.evoforge.simulation.world.bootstrap.GeneratedWorldBootstrap;
import io.github.evoforge.simulation.world.bootstrap.GeneratedWorldRuntime;
import io.github.evoforge.simulation.world.genesis.ClimateSpec;
import io.github.evoforge.simulation.world.genesis.GenerationRevision;
import io.github.evoforge.simulation.world.genesis.RngRevision;
import io.github.evoforge.simulation.world.genesis.WorldGenerationIntent;
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
import io.github.evoforge.simulation.world.scale.PhysicalSpaceScale;
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
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

final class GeneratedWorldWarmupFixture {
    private static final PhysicalSpaceScale AUDIT_SPACE_SCALE =
            PhysicalSpaceScale.cubicMillimeters(1_000L);
    private static final SimulationTimeScale AUDIT_TIME_SCALE =
            SimulationTimeScale.of(Duration.ofHours(1L));

    private GeneratedWorldWarmupFixture() { }

    static GeneratedWorldRuntime create(long seed, ClimateSpec climate) {
        return create(seed, climate, bounds(), AtmosphericForcingPolicy.CLIMATE_NORMALS);
    }

    static GeneratedWorldRuntime create(
            long seed,
            ClimateSpec climate,
            AtmosphericForcingPolicy atmosphericForcingPolicy) {
        return create(seed, climate, bounds(), atmosphericForcingPolicy);
    }

    static GeneratedWorldRuntime create(
            long seed,
            ClimateSpec climate,
            WorldBounds bounds) {
        return create(seed, climate, bounds, AtmosphericForcingPolicy.CLIMATE_NORMALS);
    }

    static GeneratedWorldRuntime create(
            long seed,
            ClimateSpec climate,
            WorldBounds bounds,
            AtmosphericForcingPolicy atmosphericForcingPolicy) {
        return create(
                WorldGenesis.current(new WorldSpec(requireBounds(bounds), climate), seed),
                atmosphericForcingPolicy,
                null);
    }

    /** Explicit revision audit seam with all physical provenance required by V8+ climate. */
    static GeneratedWorldRuntime create(
            long seed,
            ClimateSpec climate,
            WorldBounds bounds,
            AtmosphericForcingPolicy atmosphericForcingPolicy,
            GenerationRevision generationRevision) {
        if (generationRevision == null) {
            throw new IllegalArgumentException("generation revision must not be null");
        }
        WorldSpec spec = new WorldSpec(requireBounds(bounds), climate, AUDIT_SPACE_SCALE);
        return create(
                new WorldGenesis(
                        spec,
                        seed,
                        generationRevision,
                        RngRevision.V1,
                        WorldGenerationIntent.balanced()),
                atmosphericForcingPolicy,
                AUDIT_TIME_SCALE);
    }

    private static GeneratedWorldRuntime create(
            WorldGenesis genesis,
            AtmosphericForcingPolicy atmosphericForcingPolicy,
            SimulationTimeScale timeScale) {
        if (atmosphericForcingPolicy == null) {
            throw new IllegalArgumentException("atmospheric forcing policy must not be null");
        }
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

        WorldAtlasGenerator atlasGenerator = WorldAtlasGenerator.withGeology(
                new GeologyGenerationStage(geologyProfile));
        GeneratedWorldBootstrap bootstrap = timeScale == null
                ? new GeneratedWorldBootstrap(atlasGenerator, atmosphericForcingPolicy)
                : GeneratedWorldBootstrap.withTimeScale(
                        atlasGenerator,
                        atmosphericForcingPolicy,
                        timeScale);
        return bootstrap.create(genesis, assembly, terrainProfile, bindings);
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

    private static WorldBounds requireBounds(WorldBounds bounds) {
        if (bounds == null) throw new IllegalArgumentException("bounds must not be null");
        return bounds;
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
