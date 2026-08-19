package io.github.evoforge.simulation.world.terrain.shape;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.atlas.V12BaseTerrainGenerator;
import io.github.evoforge.simulation.world.atlas.V13MountainTerrainGenerator;
import io.github.evoforge.simulation.world.genesis.GenerationRevision;
import io.github.evoforge.simulation.world.genesis.MountainIntent;
import io.github.evoforge.simulation.world.genesis.RngRevision;
import io.github.evoforge.simulation.world.genesis.WorldGenerationIntent;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.genesis.WorldSpec;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import org.junit.jupiter.api.Test;

/** Acceptance guard at the generic surface-geometry boundary, without naming a concrete Shape. */
final class V13MountainShapeGenerationTest {

    @Test
    void screenshotScaleMountainsOfferCoherentGeometryToGenericShapeFitter() {
        assertScale(new WorldBounds(-50, 49, -50, 49, -12, 96), "100x100");
        assertScale(new WorldBounds(-250, 249, -250, 249, -12, 96), "500x500");
    }

    private static void assertScale(WorldBounds bounds, String label) {
        long seed = 1L;
        WorldGenerationIntent intent = new WorldGenerationIntent(
                normalized(650_000),
                normalized(750_000),
                normalized(250_000),
                normalized(600_000),
                normalized(450_000),
                normalized(500_000),
                normalized(350_000),
                new MountainIntent(
                        normalized(350_000),
                        normalized(520_000),
                        normalized(500_000),
                        normalized(550_000),
                        normalized(600_000),
                        false,
                        normalized(180_000)));
        WorldBounds baseBounds = new WorldBounds(
                bounds.minX(), bounds.maxX(), bounds.minY(), bounds.maxY(), bounds.minZ(), 12);
        ElevationField base = V12BaseTerrainGenerator.standard().generate(new WorldGenesis(
                new WorldSpec(baseBounds),
                seed,
                GenerationRevision.V12,
                RngRevision.V1,
                intent));
        ElevationField mountains = V13MountainTerrainGenerator.standard().generate(new WorldGenesis(
                new WorldSpec(bounds),
                seed,
                GenerationRevision.V13,
                RngRevision.V1,
                intent));
        TerrainShapeField shapes = TerrainShapeGenerationStage
                .forRevision(GenerationRevision.V13)
                .generate(mountains);

        long mountainCells = 0L;
        long mountainGeometry = 0L;
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                long baseHeight = base.elevationSubunitsAt(x, y);
                long finalHeight = mountains.elevationSubunitsAt(x, y);
                if (baseHeight <= 0L || finalHeight <= baseHeight) continue;
                mountainCells++;
                if (shapes.shapeOverrideAt(x, y) != null) mountainGeometry++;
            }
        }

        assertTrue(mountainCells > 0L, label + " fixture must contain dedicated mountain terrain");
        assertTrue(
                mountainGeometry * 20L >= mountainCells,
                label + " should expose generic non-full surface geometry on at least 5% of mountain cells; "
                        + "geometry=" + mountainGeometry + ", mountainCells=" + mountainCells);
    }

    private static NormalizedValue normalized(int ppm) {
        return NormalizedValue.ofPartsPerMillion(ppm);
    }
}
