package io.github.evoforge.visualizer.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;
import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import io.github.evoforge.simulation.world.terrain.shape.TerrainShapeField;
import io.github.evoforge.simulation.world.terrain.shape.TerrainSurfacePatch;

/** Top-down inspection of generated elevation and selected non-default surface geometry. */
final class WorldGenerationShape2DRenderer implements Disposable {
    private static final float LEFT_MARGIN = 24f;
    private static final float BOTTOM_MARGIN = 74f;
    private static final float TOP_MARGIN = 96f;

    private final ShapeRenderer shapes = new ShapeRenderer();
    private final Matrix4 projection = new Matrix4();

    void resize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        projection.setToOrtho2D(0f, 0f, width, height);
    }

    void render(
            ElevationField elevation,
            TerrainShapeField terrainShapes,
            WorldBounds bounds,
            int sampleWidth,
            int sampleLength,
            float rightEdge,
            boolean showSurface) {
        if (elevation == null || terrainShapes == null || bounds == null || !showSurface) return;

        float availableWidth = Math.max(1f, rightEdge - LEFT_MARGIN);
        float availableHeight = Math.max(1f, Gdx.graphics.getHeight() - TOP_MARGIN - BOTTOM_MARGIN);
        float cell = Math.min(availableWidth / sampleWidth, availableHeight / sampleLength);
        float mapWidth = cell * sampleWidth;
        float mapHeight = cell * sampleLength;
        float originX = LEFT_MARGIN + Math.max(0f, (availableWidth - mapWidth) * 0.5f);
        float originY = BOTTOM_MARGIN + Math.max(0f, (availableHeight - mapHeight) * 0.5f);
        float amplitude = Math.max(1f, Math.max(Math.abs(bounds.minZ()), Math.abs(bounds.maxZ())));

        shapes.setProjectionMatrix(projection);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (int sampleY = 0; sampleY < sampleLength; sampleY++) {
            int y = sampleCoordinate(bounds.minY(), bounds.maxY(), sampleY, sampleLength);
            for (int sampleX = 0; sampleX < sampleWidth; sampleX++) {
                int x = sampleCoordinate(bounds.minX(), bounds.maxX(), sampleX, sampleWidth);
                float height = (float) elevation.elevationSubunitsAt(x, y)
                        / ElevationField.SUBUNITS_PER_CELL;
                float normalized = MathUtils.clamp(Math.abs(height) / amplitude, 0f, 1f);
                if (height > 0f) {
                    shapes.setColor(
                            0.20f + normalized * 0.24f,
                            0.34f + normalized * 0.10f,
                            0.16f,
                            1f);
                } else {
                    shapes.setColor(
                            0.10f,
                            0.20f + normalized * 0.08f,
                            0.30f + normalized * 0.14f,
                            1f);
                }
                shapes.rect(originX + sampleX * cell, originY + sampleY * cell, cell + 0.35f, cell + 0.35f);
            }
        }
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(new Color(1f, 0.78f, 0.28f, 1f));
        for (int sampleY = 0; sampleY < sampleLength; sampleY++) {
            int y = sampleCoordinate(bounds.minY(), bounds.maxY(), sampleY, sampleLength);
            for (int sampleX = 0; sampleX < sampleWidth; sampleX++) {
                int x = sampleCoordinate(bounds.minX(), bounds.maxX(), sampleX, sampleWidth);
                if (terrainShapes.shapeOverrideAt(x, y) == null) continue;
                TerrainSurfacePatch surface = terrainShapes.surfaceAt(x, y);
                float gx = surface.gradientXSubunits();
                float gy = surface.gradientYSubunits();
                float magnitude = (float) Math.sqrt(gx * gx + gy * gy);
                if (magnitude <= 0f) continue;
                float dx = gx / magnitude;
                float dy = gy / magnitude;
                float centerX = originX + (sampleX + 0.5f) * cell;
                float centerY = originY + (sampleY + 0.5f) * cell;
                float half = cell * 0.32f;
                float endX = centerX + dx * half;
                float endY = centerY + dy * half;
                shapes.line(centerX - dx * half, centerY - dy * half, endX, endY);
                float head = Math.max(1.5f, cell * 0.12f);
                shapes.line(endX, endY, endX - dx * head - dy * head, endY - dy * head + dx * head);
                shapes.line(endX, endY, endX - dx * head + dy * head, endY - dy * head - dx * head);
            }
        }
        shapes.end();
    }

    @Override
    public void dispose() {
        shapes.dispose();
    }

    private static int sampleCoordinate(int min, int max, int sampleIndex, int sampleCount) {
        if (sampleCount <= 1) return min;
        long span = (long) max - min;
        return Math.toIntExact(min + span * sampleIndex / (sampleCount - 1L));
    }
}
