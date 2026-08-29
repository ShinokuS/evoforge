package io.github.evoforge.visualizer.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.geometry.FullShape;
import io.github.evoforge.simulation.world.geometry.Shape;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import io.github.evoforge.simulation.world.terrain.shape.TerrainShapeField;
import io.github.evoforge.simulation.world.terrain.shape.TerrainSurfacePatch;
import io.github.evoforge.visualizer.VisualizerCamera;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class WorldGenerationDetailTerrainShapeFieldTest {

    @Test
    void firstCellDetailFrameUsesSafeProjectionWithoutTouchingLazySource() {
        AtomicInteger surfaceCalls = new AtomicInteger();
        AtomicInteger shapeCalls = new AtomicInteger();
        TerrainShapeField source = countingField(surfaceCalls, shapeCalls);
        ElevationField elevation = flatElevation(source.bounds());
        VisualizerCamera.VisibleRange visible = new VisualizerCamera.VisibleRange(400, 449, 500, 533);

        WorldGenerationDetailTerrainShapeField.refinementEnabledForTests(false);
        try {
            TerrainShapeField presentation = WorldGenerationDetailTerrainShapeField.preload(
                    source,
                    elevation,
                    visible,
                    true);

            for (int x = visible.minX(); x <= visible.maxX(); x++) {
                for (int y = visible.minY(); y <= visible.maxY(); y++) {
                    assertNull(presentation.shapeOverrideAt(x, y));
                    assertEquals(TerrainSurfacePatch.flatTop(), presentation.surfaceAt(x, y));
                }
            }

            assertEquals(0, shapeCalls.get());
            assertEquals(0, surfaceCalls.get());
        } finally {
            WorldGenerationDetailTerrainShapeField.invalidate(source);
            WorldGenerationDetailTerrainShapeField.refinementEnabledForTests(true);
        }
    }

    private static TerrainShapeField countingField(
            AtomicInteger surfaceCalls,
            AtomicInteger shapeCalls) {
        return new TerrainShapeField() {
            private final WorldBounds bounds = new WorldBounds(0, 999, 0, 999, -96, 96);

            @Override
            public WorldBounds bounds() {
                return bounds;
            }

            @Override
            public TerrainSurfacePatch surfaceAt(int x, int y) {
                surfaceCalls.incrementAndGet();
                return TerrainSurfacePatch.cardinalRamp(1, 0);
            }

            @Override
            public Shape shapeOverrideAt(int x, int y) {
                shapeCalls.incrementAndGet();
                return FullShape.INSTANCE;
            }

            @Override
            public long overrideCount() {
                return 1_000_000L;
            }
        };
    }

    private static ElevationField flatElevation(WorldBounds bounds) {
        return new ElevationField() {
            @Override
            public WorldBounds bounds() {
                return bounds;
            }

            @Override
            public int elevationAt(int x, int y) {
                return 0;
            }
        };
    }
}
