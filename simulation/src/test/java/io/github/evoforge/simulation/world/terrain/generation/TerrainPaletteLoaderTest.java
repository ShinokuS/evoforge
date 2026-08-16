package io.github.evoforge.simulation.world.terrain.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

final class TerrainPaletteLoaderTest {

    @Test
    void canonicalPaletteStaysSmallAndHumanReadable() {
        TerrainPalette palette = new TerrainPaletteLoader().load(
                canonicalPalette());

        assertEquals("core:temperate_terrain", palette.key());
        assertEquals(2, palette.presets().size());
        assertTrue(palette.has(TerrainPresetCapability.GROUND_PROFILE));
        assertTrue(palette.has(TerrainPresetCapability.SURFACE_DEPOSITION));
        assertEquals("core:topsoil", palette.materials().topsoil().value());
        assertEquals("core:soil", palette.materials().soil().value());
        assertEquals("core:sand", palette.materials().sand().value());
        assertEquals("core:granite", palette.materials().rock().value());
    }

    @Test
    void unknownAuthoringFieldsFailInsteadOfBeingSilentlyIgnored() {
        String json = """
                {
                  "key":"test:terrain",
                  "presets":["core:natural_ground"],
                  "magicSlope":0.42,
                  "materials":{
                    "topsoil":"core:topsoil",
                    "soil":"core:soil",
                    "sand":"core:sand",
                    "rock":"core:granite"
                  }
                }
                """;

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new TerrainPaletteLoader().load(
                        new StringReader(json),
                        "inline"));
        assertTrue(error.getMessage().contains("unknown root field"));
    }

    @Test
    void presetCapabilityConflictsAreExplicit() {
        TerrainPresetCatalog catalog = new TerrainPresetCatalog(List.of(
                new TerrainPreset(
                        "test:ground_a",
                        TerrainPresetCapability.GROUND_PROFILE),
                new TerrainPreset(
                        "test:ground_b",
                        TerrainPresetCapability.GROUND_PROFILE)));
        String json = """
                {
                  "key":"test:terrain",
                  "presets":["test:ground_a","test:ground_b"],
                  "materials":{
                    "topsoil":"core:topsoil",
                    "soil":"core:soil",
                    "sand":"core:sand",
                    "rock":"core:granite"
                  }
                }
                """;

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new TerrainPaletteLoader(catalog).load(
                        new StringReader(json),
                        "inline"));
        assertTrue(error.getMessage().contains("capability conflict"));
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
