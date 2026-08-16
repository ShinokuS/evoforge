package io.github.evoforge.simulation.world.warmup;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.atlas.WorldAtlas;
import io.github.evoforge.simulation.world.atlas.WorldAtlasGenerator;
import io.github.evoforge.simulation.world.diagnostics.GeneratedTerrainMaterialDiagnostics;
import io.github.evoforge.simulation.world.diagnostics.GeneratedTerrainMaterialDiagnosticsFormat;
import io.github.evoforge.simulation.world.diagnostics.GeneratedTerrainMaterialDiagnosticsProbe;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.genesis.WorldSpec;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import io.github.evoforge.simulation.world.terrain.generation.CompiledTerrainProfile;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialField;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialGenerationStage;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialRole;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

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
        int side = Integer.getInteger("evoforge.generated.audit.side", 32);
        if (side < 8 || side > 128) {
            throw new IllegalArgumentException(
                    "evoforge.generated.audit.side must be between 8 and 128");
        }

        CompiledTerrainProfile profile = GeneratedWorldWarmupFixture.terrainProfile();
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
            TerrainMaterialField materials = new TerrainMaterialGenerationStage().generate(
                    atlas.elevation(),
                    atlas.drainage(),
                    profile);
            GeneratedTerrainMaterialDiagnostics diagnostics =
                    new GeneratedTerrainMaterialDiagnosticsProbe().snapshot(
                            atlas,
                            materials,
                            profile);

            topsoil += diagnostics.volumeCount(
                    profile.materials().require(TerrainMaterialRole.SURFACE));
            soil += diagnostics.volumeCount(
                    profile.materials().require(TerrainMaterialRole.SUBSURFACE));
            sand += diagnostics.volumeCount(
                    profile.materials().require(TerrainMaterialRole.SEDIMENT));
            rock += diagnostics.volumeCount(
                    profile.materials().require(TerrainMaterialRole.BEDROCK));
            surfaceTopsoil += diagnostics.surfaceCount(
                    profile.materials().require(TerrainMaterialRole.SURFACE));
            surfaceSand += diagnostics.surfaceCount(
                    profile.materials().require(TerrainMaterialRole.SEDIMENT));
            surfaceRock += diagnostics.surfaceCount(
                    profile.materials().require(TerrainMaterialRole.BEDROCK));

            System.out.println(
                    "side=" + side + " "
                            + GeneratedTerrainMaterialDiagnosticsFormat.line(diagnostics));
        }

        assertTrue(topsoil > 0L, "natural-ground preset produced no surface material");
        assertTrue(soil > 0L, "natural-ground preset produced no subsurface material");
        assertTrue(rock > 0L, "ground profile produced no bedrock");
        assertTrue(sand > 0L, "depositional preset produced no sediment");
        assertTrue(surfaceTopsoil > 0L, "no surface material is exposed on representative surfaces");
        assertTrue(surfaceRock > 0L, "no bedrock is exposed on representative surfaces");
        assertTrue(surfaceSand > 0L, "no sediment is exposed on representative surfaces");
    }

    private static WorldBounds representativeBounds(int side) {
        int min = -side / 2;
        int max = min + side - 1;
        return new WorldBounds(min, max, min, max, -32, 32);
    }
}
