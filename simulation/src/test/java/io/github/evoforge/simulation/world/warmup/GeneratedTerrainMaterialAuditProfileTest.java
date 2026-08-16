package io.github.evoforge.simulation.world.warmup;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.atlas.WorldAtlas;
import io.github.evoforge.simulation.world.atlas.WorldAtlasGenerator;
import io.github.evoforge.simulation.world.diagnostics.GeneratedTerrainMaterialDiagnostics;
import io.github.evoforge.simulation.world.diagnostics.GeneratedTerrainMaterialDiagnosticsFormat;
import io.github.evoforge.simulation.world.diagnostics.GeneratedTerrainMaterialDiagnosticsProbe;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.genesis.WorldSpec;
import io.github.evoforge.simulation.world.geology.CompiledGeologyProfile;
import io.github.evoforge.simulation.world.geology.GeologyGenerationStage;
import io.github.evoforge.simulation.world.geology.GeologyMaterialKey;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import io.github.evoforge.simulation.world.terrain.generation.CompiledTerrainProfile;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialField;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialGenerationStage;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialKey;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialRole;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("generated-world-audit")
final class GeneratedTerrainMaterialAuditProfileTest {
    private static final long[] SEEDS = {0L, 1L, 42L, 991L, 123_456_789L};

    @Test
    void printsRepresentativeGeneratedTerrainMaterialComposition() {
        int side = Integer.getInteger("evoforge.generated.audit.side", 32);
        if (side < 8 || side > 128) {
            throw new IllegalArgumentException(
                    "evoforge.generated.audit.side must be between 8 and 128");
        }

        CompiledTerrainProfile terrainProfile = GeneratedWorldWarmupFixture.terrainProfile();
        CompiledGeologyProfile geologyProfile = GeneratedWorldWarmupFixture.geologyProfile();
        WorldBounds bounds = representativeBounds(side);
        long topsoil = 0L;
        long soil = 0L;
        long sand = 0L;
        long geologyVolume = 0L;
        long surfaceTopsoil = 0L;
        long surfaceSand = 0L;
        long surfaceGeology = 0L;
        Set<String> observedGeology = new HashSet<>();

        for (long seed : SEEDS) {
            WorldAtlas atlas = new WorldAtlasGenerator(new GeologyGenerationStage(geologyProfile))
                    .generate(WorldGenesis.current(new WorldSpec(bounds), seed));
            TerrainMaterialField materials = new TerrainMaterialGenerationStage().generate(
                    atlas.elevation(),
                    atlas.geology(),
                    atlas.drainage(),
                    atlas.surfaceHydrology(),
                    terrainProfile);
            GeneratedTerrainMaterialDiagnostics diagnostics =
                    new GeneratedTerrainMaterialDiagnosticsProbe().snapshot(
                            atlas,
                            materials,
                            terrainProfile);

            topsoil += diagnostics.volumeCount(
                    terrainProfile.materials().require(TerrainMaterialRole.SURFACE));
            soil += diagnostics.volumeCount(
                    terrainProfile.materials().require(TerrainMaterialRole.SUBSURFACE));
            sand += diagnostics.volumeCount(
                    terrainProfile.materials().require(TerrainMaterialRole.SEDIMENT));
            surfaceTopsoil += diagnostics.surfaceCount(
                    terrainProfile.materials().require(TerrainMaterialRole.SURFACE));
            surfaceSand += diagnostics.surfaceCount(
                    terrainProfile.materials().require(TerrainMaterialRole.SEDIMENT));

            for (GeologyMaterialKey material : geologyProfile.materials().values()) {
                TerrainMaterialKey key = TerrainMaterialKey.of(material.value());
                long volume = diagnostics.volumeCount(key);
                long surface = diagnostics.surfaceCount(key);
                geologyVolume += volume;
                surfaceGeology += surface;
                if (volume > 0L) observedGeology.add(material.value());
            }

            System.out.println(
                    "side=" + side + " "
                            + GeneratedTerrainMaterialDiagnosticsFormat.line(diagnostics));
        }

        assertTrue(topsoil > 0L, "natural-ground preset produced no surface material");
        assertTrue(soil > 0L, "natural-ground preset produced no subsurface material");
        assertTrue(sand > 0L, "depositional preset produced no sediment");
        assertTrue(geologyVolume > 0L, "generated geology produced no bedrock material");
        assertTrue(observedGeology.size() > 1, "representative worlds expose only one geology unit");
        assertTrue(surfaceTopsoil > 0L, "no surface material is exposed on representative surfaces");
        assertTrue(surfaceSand > 0L, "no sediment is exposed on representative surfaces");
        assertTrue(surfaceGeology > 0L, "no generated geology is exposed on representative surfaces");
    }

    private static WorldBounds representativeBounds(int side) {
        int min = -side / 2;
        int max = min + side - 1;
        return new WorldBounds(min, max, min, max, -32, 32);
    }
}
