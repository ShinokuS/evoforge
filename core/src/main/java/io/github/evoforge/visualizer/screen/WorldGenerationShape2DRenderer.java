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
import io.github.evoforge.simulation.world.mechanics.geometry.FullShape;
import io.github.evoforge.simulation.world.mechanics.geometry.Shape;
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

/** Scenario-style top-down inspection of generated terrain surface geometry. */
final class WorldGenerationShape2DRenderer implements Disposable {
    private static final float FIT_PADDING = 1.08f;
    private static final float DIAGNOSTIC_SHADOW_PIXELS = 5f;
    private static final float DIAGNOSTIC_STROKE_PIXELS = 2.75f;
    private static final Color DIAGNOSTIC_SHADOW =
            new Color(0.02f, 0.025f, 0.022f, 0.94f);
    private static final Color SHAPE_DIRECTION =
            new Color(1f, 0.78f, 0.08f, 1f);

    private final VisualizerCamera camera = new VisualizerCamera();
    private final SpriteBatch batch = new SpriteBatch();
    private final ShapeRenderer diagnostics = new ShapeRenderer();
    private final ProceduralLandscapePack landscapePack = new ProceduralLandscapePack();
    private final ProceduralSliceArt sliceArt = new ProceduralSliceArt();
    private final ProceduralWaterArt waterArt = new ProceduralWaterArt();
    private final SurfaceReliefEdgeArt reliefEdges = new SurfaceReliefEdgeArt();
    private final ShapePresentationRegistry shapePresentations =
            ProceduralShapePresentations.create(landscapePack, sliceArt);

    private WorldBounds bounds;
    private int viewportWidth = 1;
    private int viewportHeight = 1;
    private boolean smoothSampling;
    private boolean showShapeDirections = true;
    private float presentationSeconds;

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

    void update(float delta, boolean keyboardNavigation) {
        presentationSeconds += Math.min(Math.max(delta, 0f), 0.1f);
        if (!keyboardNavigation) return;
        int x = 0;
        int y = 0;
        if (Gdx.input.isKeyPressed(Input.Keys.A)) x--;
        if (Gdx.input.isKeyPressed(Input.Keys.D)) x++;
        if (Gdx.input.isKeyPressed(Input.Keys.S)) y--;
        if (Gdx.input.isKeyPressed(Input.Keys.W)) y++;
        if (x != 0 || y != 0) camera.pan(x, y, delta);
    }

    void zoom(float amountY) {
        camera.zoom(amountY);
    }

    void panByPixels(float deltaX, float deltaY) {
        float worldPerPixel = camera.worldUnitsPerPixel();
        camera.panBy(-deltaX * worldPerPixel, deltaY * worldPerPixel);
    }

    void fitToWorld() {
        if (bounds == null || viewportWidth <= 0 || viewportHeight <= 0) return;
        camera.fitBounds(
                bounds.minX(),
                (float) bounds.maxX() + 1f,
                bounds.minY(),
                (float) bounds.maxY() + 1f,
                FIT_PADDING);
    }

    void toggleShapeDirections() {
        showShapeDirections = !showShapeDirections;
    }

    String zoomLabel() {
        return camera.zoomLabel();
    }

    void render(
            ElevationField elevation,
            TerrainShapeField terrainShapes,
            boolean showSurface,
            boolean showOcean) {
        if (elevation == null || terrainShapes == null || bounds == null) return;

        camera.update();
        updateSampling();
        Gdx.gl.glViewport(0, 0, viewportWidth, viewportHeight);

        VisualizerCamera.VisibleRange visible = clipped(camera.visibleRange());
        if (visible != null) {
            batch.setProjectionMatrix(camera.projection());
            batch.begin();
            if (showSurface) {
                drawTerrain(batch, elevation, terrainShapes, visible);
                drawRelief(batch, elevation, visible);
            }
            if (showOcean) {
                drawOcean(batch, elevation, visible);
            }
            batch.end();

            if (showSurface && showShapeDirections) {
                drawShapeDirections(terrainShapes, visible);
            }
        }

        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    @Override
    public void dispose() {
        diagnostics.dispose();
        reliefEdges.dispose();
        waterArt.dispose();
        sliceArt.dispose();
        landscapePack.dispose();
        batch.dispose();
    }

    private void drawTerrain(
            SpriteBatch batch,
            ElevationField elevation,
            TerrainShapeField terrainShapes,
            VisualizerCamera.VisibleRange visible) {
        for (int x = visible.minX(); x <= visible.maxX(); x++) {
            for (int y = visible.minY(); y <= visible.maxY(); y++) {
                int z = elevation.elevationAt(x, y);
                Shape shape = terrainShapes.shapeOverrideAt(x, y);
                if (shape == null) shape = FullShape.INSTANCE;
                int variant = LandscapeTopology.variant(
                        x,
                        y,
                        z,
                        ProceduralLandscapePack.SURFACE_VARIANTS);
                batch.draw(
                        shapePresentations.terrainRegion(
                                shape,
                                neighbourMask(elevation, x, y, z),
                                variant,
                                false),
                        x,
                        y,
                        1f,
                        1f);
            }
        }
    }

    private void drawRelief(
            SpriteBatch batch,
            ElevationField elevation,
            VisualizerCamera.VisibleRange visible) {
        for (int x = visible.minX(); x <= visible.maxX(); x++) {
            for (int y = visible.minY(); y <= visible.maxY(); y++) {
                int z = elevation.elevationAt(x, y);
                drawReliefEdge(batch, elevation, x, y, z, x, y + 1, SurfaceReliefEdgeArt.Side.NORTH);
                drawReliefEdge(batch, elevation, x, y, z, x + 1, y, SurfaceReliefEdgeArt.Side.EAST);
                drawReliefEdge(batch, elevation, x, y, z, x, y - 1, SurfaceReliefEdgeArt.Side.SOUTH);
                drawReliefEdge(batch, elevation, x, y, z, x - 1, y, SurfaceReliefEdgeArt.Side.WEST);
            }
        }
    }

    private void drawReliefEdge(
            SpriteBatch batch,
            ElevationField elevation,
            int x,
            int y,
            int z,
            int neighbourX,
            int neighbourY,
            SurfaceReliefEdgeArt.Side side) {
        boolean neighbourPresent = elevation.contains(neighbourX, neighbourY);
        if (neighbourPresent && elevation.elevationAt(neighbourX, neighbourY) == z) return;
        boolean raised = !neighbourPresent || z > elevation.elevationAt(neighbourX, neighbourY);
        batch.draw(reliefEdges.region(side, raised), x, y, 1f, 1f);
    }

    private void drawOcean(
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

    private int neighbourMask(ElevationField elevation, int x, int y, int z) {
        int mask = 0;
        if (sameSurface(elevation, x, y + 1, z)) mask |= LandscapeTopology.N;
        if (sameSurface(elevation, x + 1, y + 1, z)) mask |= LandscapeTopology.NE;
        if (sameSurface(elevation, x + 1, y, z)) mask |= LandscapeTopology.E;
        if (sameSurface(elevation, x + 1, y - 1, z)) mask |= LandscapeTopology.SE;
        if (sameSurface(elevation, x, y - 1, z)) mask |= LandscapeTopology.S;
        if (sameSurface(elevation, x - 1, y - 1, z)) mask |= LandscapeTopology.SW;
        if (sameSurface(elevation, x - 1, y, z)) mask |= LandscapeTopology.W;
        if (sameSurface(elevation, x - 1, y + 1, z)) mask |= LandscapeTopology.NW;
        return LandscapeTopology.normalize(mask);
    }

    private static boolean sameSurface(ElevationField elevation, int x, int y, int z) {
        return elevation.contains(x, y) && elevation.elevationAt(x, y) == z;
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
