package io.github.evoforge.visualizer.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Disposable;
import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.geometry.CellFace;
import io.github.evoforge.simulation.world.geometry.FullShape;
import io.github.evoforge.simulation.world.geometry.Shape;
import io.github.evoforge.simulation.world.geometry.SurfaceBoundaryContinuity;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import io.github.evoforge.simulation.world.terrain.shape.TerrainShapeField;
import io.github.evoforge.visualizer.VisualizerCamera;
import io.github.evoforge.visualizer.presentation.ProceduralShapePresentations;
import io.github.evoforge.visualizer.presentation.ShapeDirectionDiagnostic;
import io.github.evoforge.visualizer.presentation.ShapePresentationRegistry;
import io.github.evoforge.visualizer.visual.LandscapeTopology;
import io.github.evoforge.visualizer.visual.ProceduralLandscapePack;
import io.github.evoforge.visualizer.visual.ProceduralSliceArt;
import io.github.evoforge.visualizer.visual.ProceduralWaterArt;
import io.github.evoforge.visualizer.visual.SurfaceReliefEdgeArt;
import io.github.evoforge.visualizer.visual.TerrainElevationTintShader;

/** Scenario-style top-down inspection of generated terrain surface geometry. */
final class WorldGenerationShape2DRenderer implements Disposable {
    private static final float FIT_PADDING = 1.08f;
    private static final float MIN_CAMERA_MARGIN = 2f;
    private static final float CAMERA_MARGIN_FRACTION = 0.03f;
    private static final float DIAGNOSTIC_SHADOW_PIXELS = 5f;
    private static final float DIAGNOSTIC_STROKE_PIXELS = 2.75f;
    private static final Color DIAGNOSTIC_SHADOW =
            new Color(0.02f, 0.025f, 0.022f, 0.94f);
    private static final Color SHAPE_DIRECTION =
            new Color(1f, 0.78f, 0.08f, 1f);
    private static final Color OVERVIEW_CONTOUR =
            new Color(0.12f, 0.17f, 0.09f, 0.32f);

    private final VisualizerCamera camera = new VisualizerCamera();
    private final SpriteBatch batch = new SpriteBatch(4_096);
    private final ShapeRenderer diagnostics = new ShapeRenderer(8_192);
    private final ProceduralLandscapePack landscapePack = new ProceduralLandscapePack();
    private final ProceduralSliceArt sliceArt = new ProceduralSliceArt();
    private final ProceduralWaterArt waterArt = new ProceduralWaterArt();
    private final SurfaceReliefEdgeArt reliefEdges = new SurfaceReliefEdgeArt();
    private final TerrainElevationTintShader elevationShader = new TerrainElevationTintShader();
    private final ShapePresentationRegistry shapePresentations =
            ProceduralShapePresentations.create(landscapePack, sliceArt);
    private final Color elevationColor = new Color();

    private WorldBounds bounds;
    private WorldGenerationElevationRange elevationRange = new WorldGenerationElevationRange(0L, 0L);
    private int viewportWidth = 1;
    private int viewportHeight = 1;
    private int elevationTintPpm = WorldGenerationElevationTint.DEFAULT_STRENGTH_PPM;
    private boolean smoothSampling;
    private boolean showShapeDirections;
    private float presentationSeconds;
    private long lastVisibleColumns;
    private long lastRenderedSamples;
    private int lastLodStride = 1;

    void resize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        viewportWidth = width;
        viewportHeight = height;
        camera.resize(width, height);
        fitToWorld();
    }

    void setWorldBounds(WorldBounds bounds) {
        if (bounds == null) throw new IllegalArgumentException("world bounds must not be null");
        this.bounds = bounds;
        fitToWorld();
    }

    void setElevationRange(WorldGenerationElevationRange range) {
        if (range == null) throw new IllegalArgumentException("elevation range must not be null");
        elevationRange = range;
    }

    void setElevationTintPpm(int strengthPpm) {
        if (strengthPpm < 0 || strengthPpm > WorldGenerationElevationTint.SCALE) {
            throw new IllegalArgumentException("elevation color sensitivity must be normalized ppm");
        }
        elevationTintPpm = strengthPpm;
    }

    void update(float delta, boolean keyboardNavigation) {
        presentationSeconds += Math.min(Math.max(delta, 0f), 0.1f);
        if (keyboardNavigation) {
            int x = 0;
            int y = 0;
            if (Gdx.input.isKeyPressed(Input.Keys.A)) x--;
            if (Gdx.input.isKeyPressed(Input.Keys.D)) x++;
            if (Gdx.input.isKeyPressed(Input.Keys.S)) y--;
            if (Gdx.input.isKeyPressed(Input.Keys.W)) y++;
            if (x != 0 || y != 0) camera.pan(x, y, delta);
        }
        constrainCamera();
    }

    void zoom(float amountY) {
        camera.zoom(amountY);
        constrainCamera();
    }

    void panByPixels(float deltaX, float deltaY) {
        float worldPerPixel = camera.worldUnitsPerPixel();
        camera.panBy(-deltaX * worldPerPixel, deltaY * worldPerPixel);
        constrainCamera();
    }

    void fitToWorld() {
        if (bounds == null || viewportWidth <= 0 || viewportHeight <= 0) return;
        camera.fitBounds(
                bounds.minX(),
                (float) bounds.maxX() + 1f,
                bounds.minY(),
                (float) bounds.maxY() + 1f,
                FIT_PADDING);
        constrainCamera();
    }

    void toggleShapeDirections() {
        showShapeDirections = !showShapeDirections;
    }

    String zoomLabel() {
        return camera.zoomLabel();
    }

    int lodStride() {
        return lastLodStride;
    }

    long visibleColumns() {
        return lastVisibleColumns;
    }

    long renderedSamples() {
        return lastRenderedSamples;
    }

    void render(
            ElevationField elevation,
            TerrainShapeField terrainShapes,
            boolean showSurface,
            boolean showOcean) {
        if (elevation == null || terrainShapes == null || bounds == null) return;

        constrainCamera();
        camera.update();
        updateSampling();
        Gdx.gl.glViewport(0, 0, viewportWidth, viewportHeight);

        VisualizerCamera.VisibleRange visible = clipped(camera.visibleRange());
        if (visible == null) {
            lastVisibleColumns = 0L;
            lastRenderedSamples = 0L;
            lastLodStride = 1;
            Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
            return;
        }

        int width = visible.maxX() - visible.minX() + 1;
        int length = visible.maxY() - visible.minY() + 1;
        int stride = WorldGeneration2DLod.stride(width, length);
        lastVisibleColumns = Math.multiplyExact((long) width, (long) length);
        lastRenderedSamples = WorldGeneration2DLod.sampledCells(width, length, stride);
        lastLodStride = stride;

        ElevationField presentationElevation = stride > 1 && (showSurface || showOcean)
                ? WorldGenerationOverviewElevationField.preload(elevation, visible, stride)
                : elevation;

        batch.setProjectionMatrix(camera.projection());
        batch.begin();
        if (showSurface) {
            elevationShader.apply(batch);
            if (stride == 1) {
                drawTerrainDetailed(batch, elevation, terrainShapes, visible, showOcean);
            } else {
                drawTerrainOverview(batch, presentationElevation, visible, stride, showOcean);
            }
            elevationShader.clear(batch);
            batch.setColor(Color.WHITE);
            if (stride == 1) {
                drawRelief(batch, elevation, terrainShapes, visible, showOcean);
            }
        }
        if (showOcean) {
            if (stride == 1) {
                drawOceanDetailed(batch, elevation, visible);
            } else {
                drawOceanOverview(batch, presentationElevation, visible, stride);
            }
        }
        batch.end();

        if (showSurface && stride > 1) {
            // Contours are presentation guidance rather than terrain samples. Running them on a
            // coarser grid avoids a second full overview workload while retaining readable relief.
            drawOverviewContours(presentationElevation, visible, stride * 2);
        }
        if (showSurface && showShapeDirections && stride == 1) {
            drawShapeDirections(terrainShapes, visible);
        }

        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    @Override
    public void dispose() {
        elevationShader.dispose();
        diagnostics.dispose();
        reliefEdges.dispose();
        shapePresentations.dispose();
        waterArt.dispose();
        sliceArt.dispose();
        landscapePack.dispose();
        batch.dispose();
    }

    private void drawTerrainDetailed(
            SpriteBatch batch,
            ElevationField elevation,
            TerrainShapeField terrainShapes,
            VisualizerCamera.VisibleRange visible,
            boolean oceanVisible) {
        drawTerrainBaseCells(batch, elevation, terrainShapes, visible, oceanVisible);
        drawTerrainOverrides(batch, elevation, terrainShapes, visible, oceanVisible);
    }

    private void drawTerrainBaseCells(
            SpriteBatch batch,
            ElevationField elevation,
            TerrainShapeField terrainShapes,
            VisualizerCamera.VisibleRange visible,
            boolean oceanVisible) {
        for (int x = visible.minX(); x <= visible.maxX(); x++) {
            for (int y = visible.minY(); y <= visible.maxY(); y++) {
                long height = elevation.elevationSubunitsAt(x, y);
                if (oceanVisible && height < 0L) continue;
                if (terrainShapes.shapeOverrideAt(x, y) != null) continue;
                drawTerrainCell(batch, elevation, terrainShapes, x, y, height, FullShape.INSTANCE);
            }
        }
    }

    private void drawTerrainOverrides(
            SpriteBatch batch,
            ElevationField elevation,
            TerrainShapeField terrainShapes,
            VisualizerCamera.VisibleRange visible,
            boolean oceanVisible) {
        for (int x = visible.minX(); x <= visible.maxX(); x++) {
            for (int y = visible.minY(); y <= visible.maxY(); y++) {
                long height = elevation.elevationSubunitsAt(x, y);
                if (oceanVisible && height < 0L) continue;
                Shape shape = terrainShapes.shapeOverrideAt(x, y);
                if (shape == null) continue;
                drawTerrainCell(batch, elevation, terrainShapes, x, y, height, shape);
            }
        }
    }

    private void drawTerrainCell(
            SpriteBatch batch,
            ElevationField elevation,
            TerrainShapeField terrainShapes,
            int x,
            int y,
            long heightSubunits,
            Shape shape) {
        int z = discreteZ(heightSubunits);
        int variant = LandscapeTopology.variant(
                x,
                y,
                z,
                ProceduralLandscapePack.SURFACE_VARIANTS);
        batch.setColor(WorldGenerationElevationTint.shaderColor(
                heightSubunits,
                elevationRange,
                elevationTintPpm,
                elevationColor));
        batch.draw(
                shapePresentations.terrainRegion(
                        shape,
                        neighbourMask(elevation, terrainShapes, x, y, z, shape),
                        variant,
                        false),
                x,
                y,
                1f,
                1f);
    }

    private void drawTerrainOverview(
            SpriteBatch batch,
            ElevationField elevation,
            VisualizerCamera.VisibleRange visible,
            int stride,
            boolean oceanVisible) {
        for (int x = visible.minX(); x <= visible.maxX(); x += stride) {
            int blockWidth = Math.min(stride, visible.maxX() - x + 1);
            int sampleX = x + blockWidth / 2;
            for (int y = visible.minY(); y <= visible.maxY(); y += stride) {
                int blockLength = Math.min(stride, visible.maxY() - y + 1);
                int sampleY = y + blockLength / 2;
                long height = elevation.elevationSubunitsAt(sampleX, sampleY);
                if (oceanVisible && height < 0L) continue;
                int z = discreteZ(height);
                int variant = LandscapeTopology.variant(
                        sampleX,
                        sampleY,
                        z,
                        ProceduralLandscapePack.SURFACE_VARIANTS);
                batch.setColor(WorldGenerationElevationTint.shaderColor(
                        height,
                        elevationRange,
                        elevationTintPpm,
                        elevationColor));
                batch.draw(
                        landscapePack.surface(0xFF, variant),
                        x,
                        y,
                        blockWidth,
                        blockLength);
            }
        }
    }

    private void drawOverviewContours(
            ElevationField elevation,
            VisualizerCamera.VisibleRange visible,
            int stride) {
        diagnostics.setProjectionMatrix(camera.projection());
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        diagnostics.begin(ShapeRenderer.ShapeType.Filled);
        diagnostics.setColor(OVERVIEW_CONTOUR);

        float thickness = Math.max(
                0.01f,
                Math.min(camera.worldUnitsPerPixel() * 1.15f, stride * 0.10f));
        for (int x = visible.minX(); x <= visible.maxX(); x += stride) {
            int blockWidth = Math.min(stride, visible.maxX() - x + 1);
            int sampleX = x + blockWidth / 2;
            for (int y = visible.minY(); y <= visible.maxY(); y += stride) {
                int blockLength = Math.min(stride, visible.maxY() - y + 1);
                int sampleY = y + blockLength / 2;
                long sampleElevation = elevation.elevationSubunitsAt(sampleX, sampleY);
                if (sampleElevation < 0L) continue;
                int sampleZ = discreteZ(sampleElevation);

                int eastX = x + blockWidth;
                if (eastX <= visible.maxX()) {
                    int eastWidth = Math.min(stride, visible.maxX() - eastX + 1);
                    int eastSampleX = eastX + eastWidth / 2;
                    long eastElevation = elevation.elevationSubunitsAt(eastSampleX, sampleY);
                    if (eastElevation >= 0L && discreteZ(eastElevation) != sampleZ) {
                        diagnostics.rectLine(eastX, y, eastX, y + blockLength, thickness);
                    }
                }

                int northY = y + blockLength;
                if (northY <= visible.maxY()) {
                    int northLength = Math.min(stride, visible.maxY() - northY + 1);
                    int northSampleY = northY + northLength / 2;
                    long northElevation = elevation.elevationSubunitsAt(sampleX, northSampleY);
                    if (northElevation >= 0L && discreteZ(northElevation) != sampleZ) {
                        diagnostics.rectLine(x, northY, x + blockWidth, northY, thickness);
                    }
                }
            }
        }
        diagnostics.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    private void drawRelief(
            SpriteBatch batch,
            ElevationField elevation,
            TerrainShapeField terrainShapes,
            VisualizerCamera.VisibleRange visible,
            boolean oceanVisible) {
        for (int x = visible.minX(); x <= visible.maxX(); x++) {
            for (int y = visible.minY(); y <= visible.maxY(); y++) {
                long height = elevation.elevationSubunitsAt(x, y);
                if (oceanVisible && height < 0L) continue;
                int z = discreteZ(height);
                Shape shape = shapeAt(terrainShapes, x, y);
                drawReliefEdge(batch, elevation, terrainShapes, x, y, z, shape,
                        x, y + 1, CellFace.POSITIVE_Y, SurfaceReliefEdgeArt.Side.NORTH);
                drawReliefEdge(batch, elevation, terrainShapes, x, y, z, shape,
                        x + 1, y, CellFace.POSITIVE_X, SurfaceReliefEdgeArt.Side.EAST);
                drawReliefEdge(batch, elevation, terrainShapes, x, y, z, shape,
                        x, y - 1, CellFace.NEGATIVE_Y, SurfaceReliefEdgeArt.Side.SOUTH);
                drawReliefEdge(batch, elevation, terrainShapes, x, y, z, shape,
                        x - 1, y, CellFace.NEGATIVE_X, SurfaceReliefEdgeArt.Side.WEST);
            }
        }
    }

    private void drawReliefEdge(
            SpriteBatch batch,
            ElevationField elevation,
            TerrainShapeField terrainShapes,
            int x,
            int y,
            int z,
            Shape shape,
            int neighbourX,
            int neighbourY,
            CellFace face,
            SurfaceReliefEdgeArt.Side side) {
        boolean neighbourPresent = contains(neighbourX, neighbourY);
        Shape neighbour = null;
        int neighbourZ = z;
        if (neighbourPresent) {
            neighbourZ = discreteZ(elevation.elevationSubunitsAt(neighbourX, neighbourY));
            neighbour = shapeAt(terrainShapes, neighbourX, neighbourY);
            if (SurfaceBoundaryContinuity.aligns(shape, z, face, neighbour, neighbourZ)) return;
        }

        if (!shapePresentations.genericReliefEdgeAllowed(shape, face)) return;
        if (neighbour != null
                && !shapePresentations.genericReliefEdgeAllowed(neighbour, face.opposite())) {
            return;
        }

        boolean raised = !neighbourPresent || z > neighbourZ;
        batch.draw(reliefEdges.region(side, raised), x, y, 1f, 1f);
    }

    private void drawOceanDetailed(
            SpriteBatch batch,
            ElevationField elevation,
            VisualizerCamera.VisibleRange visible) {
        int frame = (int) (presentationSeconds * 5f);
        for (int x = visible.minX(); x <= visible.maxX(); x++) {
            for (int y = visible.minY(); y <= visible.maxY(); y++) {
                if (elevation.elevationSubunitsAt(x, y) >= 0L) continue;
                batch.draw(waterArt.frame(frame), x, y, 1f, 1f);
            }
        }
    }

    private void drawOceanOverview(
            SpriteBatch batch,
            ElevationField elevation,
            VisualizerCamera.VisibleRange visible,
            int stride) {
        int frame = (int) (presentationSeconds * 5f);
        for (int x = visible.minX(); x <= visible.maxX(); x += stride) {
            int blockWidth = Math.min(stride, visible.maxX() - x + 1);
            int sampleX = x + blockWidth / 2;
            for (int y = visible.minY(); y <= visible.maxY(); y += stride) {
                int blockLength = Math.min(stride, visible.maxY() - y + 1);
                int sampleY = y + blockLength / 2;
                if (elevation.elevationSubunitsAt(sampleX, sampleY) >= 0L) continue;
                batch.draw(waterArt.frame(frame), x, y, blockWidth, blockLength);
            }
        }
    }

    private void drawShapeDirections(
            TerrainShapeField terrainShapes,
            VisualizerCamera.VisibleRange visible) {
        diagnostics.setProjectionMatrix(camera.projection());
        diagnostics.begin(ShapeRenderer.ShapeType.Filled);
        for (int x = visible.minX(); x <= visible.maxX(); x++) {
            for (int y = visible.minY(); y <= visible.maxY(); y++) {
                Shape shape = terrainShapes.shapeOverrideAt(x, y);
                if (shape == null) continue;
                ShapeDirectionDiagnostic direction = shapePresentations.directionDiagnostic(shape);
                if (!direction.visible()) continue;
                drawDiagnosticArrow(x, y, direction.x(), direction.y());
            }
        }
        diagnostics.end();
    }

    private void drawDiagnosticArrow(int x, int y, int dx, int dy) {
        float startX = x + 0.5f;
        float startY = y + 0.5f;
        float endX = startX + dx * 0.43f;
        float endY = startY + dy * 0.43f;
        float pixel = camera.worldUnitsPerPixel();

        diagnostics.setColor(DIAGNOSTIC_SHADOW);
        diagnostics.rectLine(startX, startY, endX, endY, pixel * DIAGNOSTIC_SHADOW_PIXELS);
        drawArrowHead(endX, endY, dx, dy, 0.15f + pixel * 2.2f);

        diagnostics.setColor(SHAPE_DIRECTION);
        diagnostics.rectLine(startX, startY, endX, endY, pixel * DIAGNOSTIC_STROKE_PIXELS);
        drawArrowHead(endX, endY, dx, dy, 0.15f);
    }

    private void drawArrowHead(float x, float y, float dx, float dy, float size) {
        float baseX = x - dx * size;
        float baseY = y - dy * size;
        float sideX = -dy * size * 0.72f;
        float sideY = dx * size * 0.72f;
        diagnostics.triangle(
                x,
                y,
                baseX + sideX,
                baseY + sideY,
                baseX - sideX,
                baseY - sideY);
    }

    private int neighbourMask(
            ElevationField elevation,
            TerrainShapeField terrainShapes,
            int x,
            int y,
            int z,
            Shape shape) {
        int mask = 0;
        if (terrainArtJoins(elevation, terrainShapes, shape, z, x, y + 1, CellFace.POSITIVE_Y)) {
            mask |= LandscapeTopology.N;
        }
        if (sameDiscreteSurface(elevation, x + 1, y + 1, z)) mask |= LandscapeTopology.NE;
        if (terrainArtJoins(elevation, terrainShapes, shape, z, x + 1, y, CellFace.POSITIVE_X)) {
            mask |= LandscapeTopology.E;
        }
        if (sameDiscreteSurface(elevation, x + 1, y - 1, z)) mask |= LandscapeTopology.SE;
        if (terrainArtJoins(elevation, terrainShapes, shape, z, x, y - 1, CellFace.NEGATIVE_Y)) {
            mask |= LandscapeTopology.S;
        }
        if (sameDiscreteSurface(elevation, x - 1, y - 1, z)) mask |= LandscapeTopology.SW;
        if (terrainArtJoins(elevation, terrainShapes, shape, z, x - 1, y, CellFace.NEGATIVE_X)) {
            mask |= LandscapeTopology.W;
        }
        if (sameDiscreteSurface(elevation, x - 1, y + 1, z)) mask |= LandscapeTopology.NW;
        return LandscapeTopology.normalize(mask);
    }

    private boolean terrainArtJoins(
            ElevationField elevation,
            TerrainShapeField terrainShapes,
            Shape shape,
            int z,
            int neighbourX,
            int neighbourY,
            CellFace face) {
        if (!contains(neighbourX, neighbourY)) return false;
        int neighbourZ = discreteZ(elevation.elevationSubunitsAt(neighbourX, neighbourY));
        Shape neighbour = shapeAt(terrainShapes, neighbourX, neighbourY);
        if (SurfaceBoundaryContinuity.aligns(shape, z, face, neighbour, neighbourZ)) return true;

        return shapePresentations.genericReliefEdgeAllowed(shape, face)
                && !shapePresentations.genericReliefEdgeAllowed(neighbour, face.opposite());
    }

    private static Shape shapeAt(TerrainShapeField terrainShapes, int x, int y) {
        Shape shape = terrainShapes.shapeOverrideAt(x, y);
        return shape == null ? FullShape.INSTANCE : shape;
    }

    private boolean sameDiscreteSurface(ElevationField elevation, int x, int y, int z) {
        return contains(x, y) && discreteZ(elevation.elevationSubunitsAt(x, y)) == z;
    }

    private boolean contains(int x, int y) {
        return x >= bounds.minX() && x <= bounds.maxX()
                && y >= bounds.minY() && y <= bounds.maxY();
    }

    private static int discreteZ(long heightSubunits) {
        return Math.toIntExact(Math.floorDiv(heightSubunits, ElevationField.SUBUNITS_PER_CELL));
    }

    private VisualizerCamera.VisibleRange clipped(VisualizerCamera.VisibleRange visible) {
        int minX = Math.max(visible.minX(), bounds.minX());
        int maxX = Math.min(visible.maxX(), bounds.maxX());
        int minY = Math.max(visible.minY(), bounds.minY());
        int maxY = Math.min(visible.maxY(), bounds.maxY());
        return minX > maxX || minY > maxY
                ? null
                : new VisualizerCamera.VisibleRange(minX, maxX, minY, maxY);
    }

    private void constrainCamera() {
        if (bounds == null) return;
        float width = bounds.maxX() - bounds.minX() + 1f;
        float length = bounds.maxY() - bounds.minY() + 1f;
        float margin = Math.max(
                MIN_CAMERA_MARGIN,
                Math.min(width, length) * CAMERA_MARGIN_FRACTION);
        camera.constrainToBounds(
                bounds.minX(),
                bounds.maxX() + 1f,
                bounds.minY(),
                bounds.maxY() + 1f,
                margin);
    }

    private void updateSampling() {
        boolean smooth = camera.smoothLandscapeSampling();
        if (smooth == smoothSampling) return;
        smoothSampling = smooth;
        Texture.TextureFilter filter = smooth
                ? Texture.TextureFilter.Linear
                : Texture.TextureFilter.Nearest;
        landscapePack.surface(0, 0).getTexture().setFilter(filter, filter);
        reliefEdges.setFilter(filter);
        waterArt.setFilter(filter);
    }
}
