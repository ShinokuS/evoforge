package io.github.evoforge.simulation.world.terrain.shape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.atlas.ElevationGenerationStage;
import io.github.evoforge.simulation.world.genesis.GenerationRevision;
import io.github.evoforge.simulation.world.genesis.RngRevision;
import io.github.evoforge.simulation.world.genesis.WorldGenerationIntent;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.genesis.WorldSpec;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import org.junit.jupiter.api.Test;

/** Acceptance for V12's geometry-only coherent voxel-transition policy. */
final class V12CoherentShapeGenerationTest {

    @Test
    void generatedOverridesBelongToLaterallySupportedCardinalBands() {
        WorldBounds bounds = new WorldBounds(-64, 63, -64, 63, -12, 12);
        WorldGenesis genesis = new WorldGenesis(
                new WorldSpec(bounds),
                42L,
                GenerationRevision.V12,
                RngRevision.V1,
                new WorldGenerationIntent(
                        NormalizedValue.ofPartsPerMillion(650_000),
                        NormalizedValue.ofPartsPerMillion(750_000),
                        NormalizedValue.ofPartsPerMillion(250_000),
                        NormalizedValue.ofPartsPerMillion(800_000),
                        NormalizedValue.ofPartsPerMillion(350_000)));
        ElevationField elevation = new ElevationGenerationStage().generate(genesis);
        TerrainShapeField shapes = TerrainShapeGenerationStage
                .forRevision(GenerationRevision.V12)
                .generate(elevation);

        long overrides = 0L;
        long unsupported = 0L;
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                if (shapes.shapeOverrideAt(x, y) == null) continue;
                overrides++;
                TerrainSurfacePatch patch = shapes.surfaceAt(x, y);
                int dx = Long.compare(patch.gradientXSubunits(), 0L);
                int dy = Long.compare(patch.gradientYSubunits(), 0L);
                assertEquals(1, Math.abs(dx) + Math.abs(dy));

                int sideX = -dy;
                int sideY = dx;
                if (!sameBand(shapes, bounds, x + sideX, y + sideY, patch)
                        && !sameBand(shapes, bounds, x - sideX, y - sideY, patch)) {
                    unsupported++;
                }
            }
        }

        assertTrue(overrides > 0L, "representative V12 terrain should still contain surface Shapes");
        assertEquals(
                0L,
                unsupported,
                "V12 generated Shapes must not appear as unsupported one-cell turns or isolated artifacts");
    }

    private static boolean sameBand(
            TerrainShapeField shapes,
            WorldBounds bounds,
            int x,
            int y,
            TerrainSurfacePatch expected) {
        if (x < bounds.minX() || x > bounds.maxX()
                || y < bounds.minY() || y > bounds.maxY()) {
            return false;
        }
        if (shapes.shapeOverrideAt(x, y) == null) return false;
        TerrainSurfacePatch neighbour = shapes.surfaceAt(x, y);
        return neighbour.gradientXSubunits() == expected.gradientXSubunits()
                && neighbour.gradientYSubunits() == expected.gradientYSubunits();
    }
}
