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
    void v11RepresentsBroadSmoothSlopeAtActualVoxelTransitions() {
        WorldBounds bounds = new WorldBounds(-32, 31, -8, 7, -8, 8);
        ElevationField broadSlope = field(
                bounds,
                (x, y) -> (x + 32) * 100_000 + 50_000);

        TerrainShapeField precise = TerrainShapeGenerationStage.standard().generate(broadSlope);
        TerrainShapeField voxelAware = TerrainShapeGenerationStage
                .forRevision(GenerationRevision.V11)
                .generate(broadSlope);

        assertEquals(0L, precise.overrideCount());
        assertTrue(
                voxelAware.overrideCount() >= 80L,
                "broad slopes should expose coherent shape bands where their voxel Z actually changes");
        assertTrue(
                voxelAware.overrideCount() < 64L * 16L,
                "voxel-aware fitting must not turn an entire broad slope into shape overrides");
    }

    @Test
    void v11DoesNotNormalizeAbruptOneLevelCliffIntoSurfaceShape() {
        WorldBounds bounds = new WorldBounds(-4, 3, -4, 3, -8, 8);
        ElevationField cliff = field(
                bounds,
                (x, y) -> x < 0 ? 500_000 : 1_500_000);

        TerrainShapeField shapes = TerrainShapeGenerationStage
                .forRevision(GenerationRevision.V11)
                .generate(cliff);

        assertEquals(0L, shapes.overrideCount());
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

    @Test
    void representativeV11WorldUsesRevisionAwareSurfaceGeometry() {
        WorldBounds bounds = new WorldBounds(-64, 63, -64, 63, -12, 12);
        WorldGenesis genesis = new WorldGenesis(
                new WorldSpec(bounds),
                42L,
                GenerationRevision.V11,
                RngRevision.V1,
                new WorldGenerationIntent(
                        NormalizedValue.ofPartsPerMillion(350_000),
                        NormalizedValue.ofPartsPerMillion(750_000),
                        NormalizedValue.ofPartsPerMillion(250_000),
                        NormalizedValue.ofPartsPerMillion(800_000)));
        ElevationField elevation = new ElevationGenerationStage().generate(genesis);

        TerrainShapeField shapes = TerrainShapeGenerationStage
                .forRevision(GenerationRevision.V11)
                .generate(elevation);

        assertTrue(shapes.overrideCount() > 0L);
        assertTrue(shapes.overrideCount() < 128L * 128L);
    }

    private static ElevationField field(IntBinaryOperator heights) {
        return field(BOUNDS, heights);
    }

    private static ElevationField field(WorldBounds bounds, IntBinaryOperator heights) {
        return new ElevationField() {
            @Override public WorldBounds bounds() { return bounds; }

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
