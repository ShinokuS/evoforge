package io.github.evoforge.simulation.world.terrain.shape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.atlas.ElevationGenerationStage;
import io.github.evoforge.simulation.world.genesis.GenerationRevision;
import io.github.evoforge.simulation.world.genesis.RngRevision;
import io.github.evoforge.simulation.world.genesis.WorldGenerationIntent;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.genesis.WorldSpec;
import io.github.evoforge.simulation.world.mechanics.geometry.Shape;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.List;
import java.util.function.IntBinaryOperator;
import org.junit.jupiter.api.Test;

final class TerrainShapeGenerationStageTest {
    private static final long CELL = ElevationField.SUBUNITS_PER_CELL;
    private static final WorldBounds BOUNDS = new WorldBounds(-1, 1, -1, 1, -8, 8);

    @Test
    void fitsOpaqueCandidateFromGeometryWithoutKnowingConcreteShapeType() {
        Shape positiveX = (relativeX, relativeY, relativeZ) -> 0L;
        Shape positiveY = (relativeX, relativeY, relativeZ) -> 0L;
        TerrainShapePalette palette = new TerrainShapePalette(List.of(
                TerrainShapeTemplate.baseline(TerrainSurfacePatch.flatTop()),
                TerrainShapeTemplate.shaped(TerrainSurfacePatch.cardinalRamp(1, 0), positiveX),
                TerrainShapeTemplate.shaped(TerrainSurfacePatch.cardinalRamp(0, 1), positiveY)));
        TerrainShapeGenerationStage stage = new TerrainShapeGenerationStage(
                palette,
                new TerrainShapeCalibration(1L, 1L, 1L));

        TerrainShapeField shapes = stage.generate(field((x, y) -> x * 1_000_000 + 500_000));

        assertSame(positiveX, shapes.shapeOverrideAt(0, 0));
        assertEquals(CELL, shapes.surfaceAt(0, 0).gradientXSubunits());
        assertEquals(0L, shapes.surfaceAt(0, 0).gradientYSubunits());
    }

    @Test
    void flatFractionalSurfaceDoesNotInventSlopeGeometry() {
        TerrainShapeField shapes = TerrainShapeGenerationStage.standard()
                .generate(field((x, y) -> 200_000));

        assertNull(shapes.shapeOverrideAt(0, 0));
        assertEquals(0L, shapes.surfaceAt(0, 0).reliefSubunits());
    }

    @Test
    void sharpCliffFallsBackInsteadOfBeingRepairedByRamp() {
        TerrainShapeField shapes = TerrainShapeGenerationStage.standard()
                .generate(field((x, y) -> x * 3_000_000 + 500_000));

        assertNull(shapes.shapeOverrideAt(0, 0));
    }

    @Test
    void diagonalSurfaceDoesNotBecomeFakeCardinalSlope() {
        TerrainShapeField shapes = TerrainShapeGenerationStage.standard()
                .generate(field((x, y) -> (x + y) * 700_000 + 500_000));

        assertNull(shapes.shapeOverrideAt(0, 0));
    }

    @Test
    void standardVocabularyRepresentsCleanCardinalSlopeAndIsDeterministic() {
        ElevationField elevation = field((x, y) -> x * 1_000_000 + 500_000);
        TerrainShapeGenerationStage stage = TerrainShapeGenerationStage.standard();

        TerrainShapeField first = stage.generate(elevation);
        TerrainShapeField second = stage.generate(elevation);

        assertNotNull(first.shapeOverrideAt(0, 0));
        assertEquals(CELL, first.surfaceAt(0, 0).gradientXSubunits());
        assertEquals(first.overrideCount(), second.overrideCount());
        for (int y = BOUNDS.minY(); y <= BOUNDS.maxY(); y++) {
            for (int x = BOUNDS.minX(); x <= BOUNDS.maxX(); x++) {
                assertEquals(first.surfaceAt(x, y), second.surfaceAt(x, y));
                assertSame(first.shapeOverrideAt(x, y), second.shapeOverrideAt(x, y));
            }
        }
    }

    @Test
    void representativeV10WorldActuallyUsesSupportedSurfaceGeometry() {
        WorldBounds bounds = new WorldBounds(-32, 31, -32, 31, -12, 12);
        WorldGenesis genesis = new WorldGenesis(
                new WorldSpec(bounds),
                42L,
                GenerationRevision.V10,
                RngRevision.V1,
                new WorldGenerationIntent(
                        NormalizedValue.ofPartsPerMillion(350_000),
                        NormalizedValue.ofPartsPerMillion(750_000),
                        NormalizedValue.ofPartsPerMillion(250_000),
                        NormalizedValue.ofPartsPerMillion(600_000)));
        ElevationField elevation = new ElevationGenerationStage().generate(genesis);

        TerrainShapeField shapes = TerrainShapeGenerationStage.standard().generate(elevation);

        assertTrue(
                shapes.overrideCount() > 0L,
                "representative macro terrain should expose some naturally fitting surface shapes");
        assertTrue(
                shapes.overrideCount() < 64L * 64L,
                "shape compilation must preserve ordinary full-cell terrain where no candidate fits");
    }

    private static ElevationField field(IntBinaryOperator heights) {
        return new ElevationField() {
            @Override public WorldBounds bounds() { return BOUNDS; }

            @Override
            public int elevationAt(int x, int y) {
                return Math.toIntExact(Math.floorDiv(elevationSubunitsAt(x, y), CELL));
            }

            @Override
            public long elevationSubunitsAt(int x, int y) {
                if (!contains(x, y)) throw new IllegalArgumentException("outside test elevation");
                return heights.applyAsInt(x, y);
            }
        };
    }
}
