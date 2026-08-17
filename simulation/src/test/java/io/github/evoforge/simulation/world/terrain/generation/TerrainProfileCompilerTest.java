package io.github.evoforge.simulation.world.terrain.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class TerrainProfileCompilerTest {
    @Test
    void canonicalProfileAndMaterialSetStaySmallAndHumanReadable() {
        TerrainProfileDefinition profile = new TerrainProfileLoader().load(canonicalProfile());
        TerrainMaterialSetDefinition materialSet = new TerrainMaterialSetLoader().load(
                canonicalMaterialSet());
        CompiledTerrainProfile compiled = new TerrainProfileCompiler().compile(profile, materialSet);

        assertEquals("core:temperate_terrain", compiled.key());
        assertEquals("core:temperate_ground", compiled.materials().key());
        assertEquals(2, compiled.presets().size());
        assertTrue(compiled.has(TerrainPresetCapability.GROUND_PROFILE));
        assertTrue(compiled.has(TerrainPresetCapability.SURFACE_DEPOSITION));
        assertEquals(
                "core:topsoil",
                compiled.materials().require(TerrainMaterialRole.SURFACE).value());
        assertEquals(
                "core:soil",
                compiled.materials().require(TerrainMaterialRole.SUBSURFACE).value());
        assertEquals(
                "core:sand",
                compiled.materials().require(TerrainMaterialRole.SEDIMENT).value());
        assertEquals(
                "core:granite",
                compiled.materials().require(TerrainMaterialRole.BEDROCK).value());
    }

    @Test
    void profileLoaderRejectsUnknownFieldsWithoutKnowingPresetCatalog() {
        String json = """
                {
                  "key":"test:terrain",
                  "presets":["core:natural_ground"],
                  "materialSet":"test:materials",
                  "magicSlope":0.42
                }
                """;

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new TerrainProfileLoader().load(new StringReader(json), "inline"));
        assertTrue(error.getMessage().contains("unknown root field"));
    }

    @Test
    void materialSetLoaderRejectsOldConcreteRoleNames() {
        String json = """
                {
                  "key":"test:materials",
                  "bindings":{
                    "topsoil":"core:topsoil"
                  }
                }
                """;

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new TerrainMaterialSetLoader().load(new StringReader(json), "inline"));
        assertTrue(error.getMessage().contains("unknown bindings field"));
    }

    @Test
    void presetCapabilityConflictsAreCompileErrorsNotParsingErrors() {
        TerrainPresetCatalog catalog = new TerrainPresetCatalog(List.of(
                new TerrainPreset(
                        "test:ground_a",
                        TerrainPresetCapability.GROUND_PROFILE,
                        TerrainMaterialRole.SURFACE,
                        TerrainMaterialRole.SUBSURFACE,
                        TerrainMaterialRole.BEDROCK),
                new TerrainPreset(
                        "test:ground_b",
                        TerrainPresetCapability.GROUND_PROFILE,
                        TerrainMaterialRole.SURFACE,
                        TerrainMaterialRole.SUBSURFACE,
                        TerrainMaterialRole.BEDROCK)));
        TerrainProfileDefinition profile = new TerrainProfileDefinition(
                "test:terrain",
                List.of("test:ground_a", "test:ground_b"),
                "test:materials");
        TerrainMaterialSetDefinition materials = new TerrainMaterialSetDefinition(
                "test:materials",
                groundMaterials());

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new TerrainProfileCompiler(catalog).compile(profile, materials));
        assertTrue(error.getMessage().contains("capability conflict"));
    }

    @Test
    void missingRequiredMaterialRoleFailsBeforeGeneration() {
        TerrainProfileDefinition profile = new TerrainProfileDefinition(
                "test:terrain",
                List.of(TerrainPresetCatalog.NATURAL_GROUND),
                "test:materials");
        TerrainMaterialSetDefinition materials = new TerrainMaterialSetDefinition(
                "test:materials",
                Map.of(
                        TerrainMaterialRole.SURFACE,
                        TerrainMaterialKey.of("test:surface"),
                        TerrainMaterialRole.BEDROCK,
                        TerrainMaterialKey.of("test:bedrock")));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new TerrainProfileCompiler().compile(profile, materials));
        assertTrue(error.getMessage().contains("subsurface"));
    }

    @Test
    void suppliedMaterialSetMustMatchAuthoredReference() {
        TerrainProfileDefinition profile = new TerrainProfileDefinition(
                "test:terrain",
                List.of(TerrainPresetCatalog.NATURAL_GROUND),
                "test:expected");
        TerrainMaterialSetDefinition materials = new TerrainMaterialSetDefinition(
                "test:other",
                groundMaterials());

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new TerrainProfileCompiler().compile(profile, materials));
        assertTrue(error.getMessage().contains("does not match"));
    }

    private static Map<TerrainMaterialRole, TerrainMaterialKey> groundMaterials() {
        return Map.of(
                TerrainMaterialRole.SURFACE, TerrainMaterialKey.of("test:surface"),
                TerrainMaterialRole.SUBSURFACE, TerrainMaterialKey.of("test:subsurface"),
                TerrainMaterialRole.BEDROCK, TerrainMaterialKey.of("test:bedrock"));
    }

    private static Path canonicalProfile() {
        return findAsset("assets/definitions/worldgen/terrain/temperate.json");
    }

    private static Path canonicalMaterialSet() {
        return findAsset(
                "assets/definitions/worldgen/terrain/material-sets/temperate-ground.json");
    }

    private static Path findAsset(String relative) {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relative);
            if (Files.isRegularFile(candidate)) return candidate;
            current = current.getParent();
        }
        throw new IllegalStateException("canonical terrain asset not found: " + relative);
    }
}
