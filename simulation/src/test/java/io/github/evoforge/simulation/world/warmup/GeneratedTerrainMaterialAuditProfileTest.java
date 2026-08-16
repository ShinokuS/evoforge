package io.github.evoforge.simulation.world.warmup;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.world.atlas.WorldAtlas;
import io.github.evoforge.simulation.world.atlas.WorldAtlasGenerator;
import io.github.evoforge.simulation.world.diagnostics.GeneratedTerrainMaterialDiagnostics;
import io.github.evoforge.simulation.world.diagnostics.GeneratedTerrainMaterialDiagnosticsFormat;
import io.github.evoforge.simulation.world.diagnostics.GeneratedTerrainMaterialDiagnosticsProbe;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.genesis.WorldSpec;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialField;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialGenerationStage;
import io.github.evoforge.simulation.world.terrain.generation.TerrainPalette;
import io.github.evoforge.simulation.world.terrain.generation.TerrainPaletteLoader;

@Tag("generated-world-audit")
final class GeneratedTerrainMaterialAuditProfileTest {

    private static final long[] SEEDS = {
            0L,
            1L,
            42L,
            991L,
            123_456_789L
    };

    @Test
    void printsRepresentativeGeneratedTerrainMaterialComposition() {
        int side = Integer.getInteger(
                "evoforge.generated.audit.side",
                32);
        if (side < 8 || side > 128) {
            throw new IllegalArgumentException(
                    "evoforge.generated.audit.side must be between 8 and 128");
        }

        TerrainPalette palette = new TerrainPaletteLoader().load(
                canonicalPalette());
        WorldBounds bounds = representativeBounds(side);
        long topsoil = 0L;
        long soil = 0L;
        long sand = 0L;
        long rock = 0L;
        long surfaceTopsoil = 0L;
        long surfaceSand = 0L;
        long surfaceRock = 0L;

        for (long seed : SEEDS) {
            WorldAtlas atlas = new WorldAtlasGenerator().generate(
                    WorldGenesis.current(new WorldSpec(bounds), seed));
            TerrainMaterialField materials =
                    new TerrainMaterialGenerationStage().generate(
                            atlas.elevation(),
                            atlas.drainage(),
                            palette);
            GeneratedTerrainMaterialDiagnostics diagnostics =
                    new GeneratedTerrainMaterialDiagnosticsProbe().snapshot(
                            atlas,
                            materials,
                            palette);

            topsoil += diagnostics.volumeCount(palette.materials().topsoil());
            soil += diagnostics.volumeCount(palette.materials().soil());
            sand += diagnostics.volumeCount(palette.materials().sand());
            rock += diagnostics.volumeCount(palette.materials().rock());
            surfaceTopsoil += diagnostics.surfaceCount(palette.materials().topsoil());
            surfaceSand += diagnostics.surfaceCount(palette.materials().sand());
            surfaceRock += diagnostics.surfaceCount(palette.materials().rock());

            System.out.println(
                    "side=" + side + " "
                            + GeneratedTerrainMaterialDiagnosticsFormat.line(diagnostics));
        }

        assertTrue(topsoil > 0L, "natural-ground preset produced no topsoil");
        assertTrue(soil > 0L, "natural-ground preset produced no soil");
        assertTrue(rock > 0L, "ground profile produced no bedrock");
        assertTrue(sand > 0L, "depositional-sand preset produced no sand");
        assertTrue(surfaceTopsoil > 0L, "no topsoil is exposed on representative surfaces");
        assertTrue(surfaceRock > 0L, "no bedrock is exposed on representative surfaces");
        assertTrue(surfaceSand > 0L, "no deposited sand is exposed on representative surfaces");
    }

    private static WorldBounds representativeBounds(int side) {
        int min = -side / 2;
        int max = min + side - 1;
        return new WorldBounds(min, max, min, max, -32, 32);
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
