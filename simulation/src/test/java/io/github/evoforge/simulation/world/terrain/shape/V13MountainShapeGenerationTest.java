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

/** Acceptance guard that stays at the abstract surface-geometry boundary. */
final class V13MountainShapeGenerationTest {
    private static final WorldBounds V13_BOUNDS = new WorldBounds(-64, 63, -64, 63, -12, 96);
    private static final WorldBounds V12_BOUNDS = new WorldBounds(-64, 63, -64, 63, -12, 12);

    @Test
    void dedicatedMountainAreaOffersCoherentGeometryToGenericShapeFitter() {
        long seed = 31_337L;
        WorldGenerationIntent intent = new WorldGenerationIntent(
                normalized(650_000),
                normalized(750_000),
                normalized(250_000),
                normalized(600_000),
                normalized(450_000),
                normalized(500_000),
                normalized(350_000),
                new MountainIntent(
                        normalized(1_000_000),
                        normalized(700_000),
                        normalized(500_000),
                        normalized(550_000),
                        normalized(600_000),
                        false,
                        normalized(0)));

        ElevationField base = V12BaseTerrainGenerator.standard().generate(new WorldGenesis(
                new WorldSpec(V12_BOUNDS),
                seed,
                GenerationRevision.V12,
                RngRevision.V1,
                intent));
        ElevationField mountains = V13MountainTerrainGenerator.standard().generate(new WorldGenesis(
                new WorldSpec(V13_BOUNDS),
                seed,
                GenerationRevision.V13,
                RngRevision.V1,
                intent));
        TerrainShapeField shapes = TerrainShapeGenerationStage
                .forRevision(GenerationRevision.V13)
                .generate(mountains);

        long mountainCells = 0L;
        long mountainGeometry = 0L;
        for (int y = V13_BOUNDS.minY(); y <= V13_BOUNDS.maxY(); y++) {
            for (int x = V13_BOUNDS.minX(); x <= V13_BOUNDS.maxX(); x++) {
                long baseHeight = base.elevationSubunitsAt(x, y);
                long finalHeight = mountains.elevationSubunitsAt(x, y);
                if (baseHeight <= 0L || finalHeight == baseHeight) continue;
                mountainCells++;
                if (shapes.shapeOverrideAt(x, y) != null) mountainGeometry++;
            }
        }

        assertTrue(mountainCells > 500L, "representative world must contain a substantial mountain footprint");
        assertTrue(
                mountainGeometry * 100L >= mountainCells,
                "at least one percent of the mountain footprint should be representable by generic non-full geometry");
    }

    private static NormalizedValue normalized(int ppm) {
        return NormalizedValue.ofPartsPerMillion(ppm);
    }
}
