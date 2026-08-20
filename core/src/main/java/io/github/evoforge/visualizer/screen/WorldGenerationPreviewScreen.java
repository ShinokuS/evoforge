package io.github.evoforge.visualizer.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.atlas.ElevationGenerationStage;
import io.github.evoforge.simulation.world.atlas.hydrology.StandingWaterBoundaryRoute;
import io.github.evoforge.simulation.world.atlas.hydrology.StandingWaterHydrologyTopology;
import io.github.evoforge.simulation.world.atlas.hydrology.StandingWaterHydrologyTopologyStage;
import io.github.evoforge.simulation.world.genesis.GenerationRevision;
import io.github.evoforge.simulation.world.genesis.RngRevision;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.genesis.WorldSpec;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import io.github.evoforge.simulation.world.terrain.shape.TerrainShapeField;
import io.github.evoforge.simulation.world.terrain.shape.TerrainShapeGenerationStage;
import io.github.evoforge.visualizer.VisualizerPerformanceTelemetry;
import java.util.ArrayList;
import java.util.List;

/** Interactive 2D/3D inspection workspace for generated world morphology and surface geometry. */
public final class WorldGenerationPreviewScreen extends ScreenAdapter {
    private static final GenerationRevision PREVIEW_REVISION = GenerationRevision.V15;
    private static final float VERTICAL_EXAGGERATION = 1.35f;
    private static final int SURFACE_CHUNK_INTERVALS = 128;

    private static final String VERTEX_SHADER = """
            attribute vec3 a_position;
            attribute vec4 a_color;
            uniform mat4 u_projView;
            varying vec4 v_color;
            void main() {
                v_color = a_color;
                gl_Position = u_projView * vec4(a_position, 1.0);
            }
            """;
    private static final String FRAGMENT_SHADER = """
            #ifdef GL_ES
            precision mediump float;
            #endif
            varying vec4 v_color;
            void main() {
                gl_FragColor = v_color;
            }
            """;

    private final Runnable returnToWorkspace;
    private final WorldGenerationPreviewSettings settings = new WorldGenerationPreviewSettings();
    private final PerspectiveCamera camera = new PerspectiveCamera();
    private final SpriteBatch batch = new SpriteBatch();
    private final BitmapFont font = new BitmapFont();
    private final ShaderProgram shader = new ShaderProgram(VERTEX_SHADER, FRAGMENT_SHADER);
    private final PreviewInput input = new PreviewInput();
    private final WorldGenerationShape2DRenderer shape2DRenderer = new WorldGenerationShape2DRenderer();
    private final WorldGenerationHydrologyDiagnosticRenderer hydrologyDiagnosticRenderer =
            new WorldGenerationHydrologyDiagnosticRenderer();
    private final VisualizerPerformanceTelemetry performance = new VisualizerPerformanceTelemetry();
    private final WorldGenerationSettingsPanel settingsPanel;
    private final InputMultiplexer inputMultiplexer;

    private WorldGenerationPreviewConfig generatedConfig;
    private WorldBounds bounds;
    private ElevationField generatedElevation;
    private WorldGenerationElevationRange elevationRange = new WorldGenerationElevationRange(0L, 0L);
    private TerrainShapeField generatedShapes;
    private StandingWaterHydrologyTopology generatedHydrologyTopology;
    private Mesh[] surfaceMeshes = new Mesh[0];
    private Mesh oceanMesh;
    private boolean showSurface = true;
    private boolean showOcean = true;
    private boolean twoDimensional;
    private boolean showHydrologyDiagnostics;
    private int elevationTintPpm = WorldGenerationElevationTint.DEFAULT_STRENGTH_PPM;
    private float yaw = 45f;
    private float pitch = 42f;
    private float distance = 86f;
    private int previewWidth;
    private int previewLength;
    private double generationMillis;
    private double lastCpuMillis;
    private int lastMouseX;
    private int lastMouseY;
    private boolean orbiting;
    private boolean panning2D;

    public WorldGenerationPreviewScreen(Runnable returnToWorkspace) {
        if (returnToWorkspace == null) {
            throw new IllegalArgumentException("returnToWorkspace must not be null");
        }
        if (!shader.isCompiled()) {
            throw new IllegalStateException("world preview shader failed: " + shader.getLog());
        }
        this.returnToWorkspace = returnToWorkspace;
        this.settingsPanel = new WorldGenerationSettingsPanel(
                settings,
                this::regenerate,
                showSurface,
                showOcean,
                twoDimensional,
                elevationTintPpm,
                visible -> showSurface = visible,
                visible -> showOcean = visible,
                visible -> {
                    twoDimensional = visible;
                    orbiting = false;
                    panning2D = false;
                },
                this::setElevationTintPpm,
                this::set3DMeshAxis);
        this.inputMultiplexer = new InputMultiplexer(input, settingsPanel.inputProcessor());
        shape2DRenderer.setElevationTintPpm(elevationTintPpm);
        regenerate();
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(inputMultiplexer);
    }

    @Override
    public void render(float delta) {
        long frameStart = System.nanoTime();
        Gdx.gl.glClearColor(0.025f, 0.035f, 0.05f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        long afterUpdate;
        if (twoDimensional) {
            boolean keyboardNavigation = !settingsPanel.keyboardInputActive();
            shape2DRenderer.update(delta, keyboardNavigation);
            hydrologyDiagnosticRenderer.update(delta, keyboardNavigation);
            afterUpdate = System.nanoTime();
            renderTwoDimensional();
        } else {
            afterUpdate = System.nanoTime();
            renderThreeDimensional();
        }
        long afterWorld = System.nanoTime();
        drawOverlay();
        long afterOverlay = System.nanoTime();
        settingsPanel.render(delta);
        long frameEnd = System.nanoTime();

        lastCpuMillis = (frameEnd - frameStart) / 1_000_000d;
        performance.record(
                delta,
                frameEnd - frameStart,
                afterUpdate - frameStart,
                afterWorld - afterUpdate,
                afterOverlay - afterWorld,
                frameEnd - afterOverlay);
    }

    private void renderThreeDimensional() {
        updateCamera();
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        shader.bind();
        shader.setUniformMatrix("u_projView", camera.combined);
        if (showSurface) {
            for (Mesh surfaceMesh : surfaceMeshes) {
                surfaceMesh.render(shader, GL20.GL_TRIANGLES);
            }
        }
        if (showOcean && oceanMesh != null) {
            Gdx.gl.glEnable(GL20.GL_BLEND);
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
            oceanMesh.render(shader, GL20.GL_TRIANGLES);
            Gdx.gl.glDisable(GL20.GL_BLEND);
        }
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
    }

    private void renderTwoDimensional() {
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        shape2DRenderer.render(
                generatedElevation,
                generatedShapes,
                showSurface,
                showOcean);
        if (showHydrologyDiagnostics) {
            ensureHydrologyTopology();
            hydrologyDiagnosticRenderer.render(generatedHydrologyTopology);
        }
    }

    @Override
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        camera.fieldOfView = 55f;
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.near = 0.1f;
        camera.far = cameraFarPlane();
        camera.update();
        batch.getProjectionMatrix().setToOrtho2D(0f, 0f, width, height);
        settingsPanel.resize(width, height);
        int previewViewportWidth = Math.max(1, Math.round(settingsPanel.previewRightEdge()));
        shape2DRenderer.resize(previewViewportWidth, height);
        hydrologyDiagnosticRenderer.resize(previewViewportWidth, height);
    }

    @Override
    public void hide() {
        orbiting = false;
        panning2D = false;
        if (Gdx.input.getInputProcessor() == inputMultiplexer) {
            Gdx.input.setInputProcessor(null);
        }
    }

    @Override
    public void dispose() {
        hide();
        disposeMeshes();
        shape2DRenderer.dispose();
        hydrologyDiagnosticRenderer.dispose();
        settingsPanel.dispose();
        shader.dispose();
        batch.dispose();
        font.dispose();
    }

    private void regenerate() {
        WorldGenerationPreviewConfig previous = generatedConfig;
        generatedConfig = settings.snapshot();
        bounds = generatedConfig.bounds();
        WorldGenesis genesis = new WorldGenesis(
                new WorldSpec(bounds),
                generatedConfig.seed(),
                PREVIEW_REVISION,
                RngRevision.V1,
                generatedConfig.intent());

        long started = System.nanoTime();
        generatedElevation = new ElevationGenerationStage().generate(genesis);
        generatedShapes = TerrainShapeGenerationStage
                .forRevision(PREVIEW_REVISION)
                .generate(generatedElevation);
        elevationRange = WorldGenerationElevationRange.from(generatedElevation);
        generationMillis = (System.nanoTime() - started) / 1_000_000d;
        generatedHydrologyTopology = null;

        disposeMeshes();
        rebuildSurfaceMeshes();
        oceanMesh = buildOcean(bounds);
        shape2DRenderer.setWorldBounds(bounds);
        shape2DRenderer.setElevationRange(elevationRange);
        shape2DRenderer.setElevationTintPpm(elevationTintPpm);
        hydrologyDiagnosticRenderer.setWorldBounds(bounds);
        camera.far = cameraFarPlane();

        if (showHydrologyDiagnostics) {
            ensureHydrologyTopology();
        }

        if (previous == null
                || previous.width() != generatedConfig.width()
                || previous.length() != generatedConfig.length()) {
            fitCameraToWorld();
            shape2DRenderer.fitToWorld();
            hydrologyDiagnosticRenderer.fitToWorld();
        }
    }

    private void ensureHydrologyTopology() {
        if (generatedHydrologyTopology != null || generatedElevation == null) return;
        generatedHydrologyTopology = StandingWaterHydrologyTopologyStage.standard()
                .generate(generatedElevation);
    }

    private void setElevationTintPpm(int strengthPpm) {
        if (strengthPpm < 0 || strengthPpm > WorldGenerationElevationTint.SCALE) {
            throw new IllegalArgumentException("elevation color sensitivity must be normalized ppm");
        }
        if (elevationTintPpm == strengthPpm) return;
        elevationTintPpm = strengthPpm;
        shape2DRenderer.setElevationTintPpm(strengthPpm);
        rebuildSurfaceMeshes();
    }

    private void set3DMeshAxis(int samples) {
        if (WorldGeneration3DDetail.maxAxisSamples() == samples) return;
        WorldGeneration3DDetail.maxAxisSamples(samples);
        rebuildSurfaceMeshes();
    }

    private void rebuildSurfaceMeshes() {
        if (generatedElevation == null || bounds == null || generatedConfig == null) return;
        previewWidth = WorldGeneration3DDetail.sampleCount(generatedConfig.width());
        previewLength = WorldGeneration3DDetail.sampleCount(generatedConfig.length());
        disposeSurfaceMeshes();
        surfaceMeshes = buildSurfaceMeshes(
                generatedElevation,
                bounds,
                elevationRange,
                previewWidth,
                previewLength,
                elevationTintPpm);
    }

    private static Mesh[] buildSurfaceMeshes(
            ElevationField elevation,
            WorldBounds bounds,
            WorldGenerationElevationRange elevationRange,
            int sampleWidth,
            int sampleLength,
            int elevationTintPpm) {
        List<Mesh> meshes = new ArrayList<>();
        for (int startY = 0; startY < sampleLength - 1; startY += SURFACE_CHUNK_INTERVALS) {
            int endY = Math.min(sampleLength - 1, startY + SURFACE_CHUNK_INTERVALS);
            for (int startX = 0; startX < sampleWidth - 1; startX += SURFACE_CHUNK_INTERVALS) {
                int endX = Math.min(sampleWidth - 1, startX + SURFACE_CHUNK_INTERVALS);
                meshes.add(buildSurfaceChunk(
                        elevation,
                        bounds,
                        elevationRange,
                        sampleWidth,
                        sampleLength,
                        startX,
                        endX,
                        startY,
                        endY,
                        elevationTintPpm));
            }
        }
        return meshes.toArray(Mesh[]::new);
    }

    private static Mesh buildSurfaceChunk(
            ElevationField elevation,
            WorldBounds bounds,
            WorldGenerationElevationRange elevationRange,
            int globalSampleWidth,
            int globalSampleLength,
            int startSampleX,
            int endSampleX,
            int startSampleY,
            int endSampleY,
            int elevationTintPpm) {
        int sampleWidth = endSampleX - startSampleX + 1;
        int sampleLength = endSampleY - startSampleY + 1;
        int vertexCount = Math.multiplyExact(sampleWidth, sampleLength);
        float[] vertices = new float[vertexCount * 7];
        Color color = new Color();
        int cursor = 0;
        for (int localY = 0; localY < sampleLength; localY++) {
            int sampleY = startSampleY + localY;
            int y = sampleCoordinate(
                    bounds.minY(), bounds.maxY(), sampleY, globalSampleLength);
            for (int localX = 0; localX < sampleWidth; localX++) {
                int sampleX = startSampleX + localX;
                int x = sampleCoordinate(
                        bounds.minX(), bounds.maxX(), sampleX, globalSampleWidth);
                long heightSubunits = elevation.elevationSubunitsAt(x, y);
                float h = (float) heightSubunits / ElevationField.SUBUNITS_PER_CELL;
                WorldGenerationElevationTint.color(
                        heightSubunits,
                        elevationRange,
                        elevationTintPpm,
                        color);
                vertices[cursor++] = x;
                vertices[cursor++] = h * VERTICAL_EXAGGERATION;
                vertices[cursor++] = -y;
                vertices[cursor++] = color.r;
                vertices[cursor++] = color.g;
                vertices[cursor++] = color.b;
                vertices[cursor++] = color.a;
            }
        }

        short[] indices = new short[(sampleWidth - 1) * (sampleLength - 1) * 6];
        int index = 0;
        for (int y = 0; y < sampleLength - 1; y++) {
            for (int x = 0; x < sampleWidth - 1; x++) {
                int a = y * sampleWidth + x;
                int b = a + 1;
                int c = a + sampleWidth;
                int d = c + 1;
                indices[index++] = (short) a;
                indices[index++] = (short) b;
                indices[index++] = (short) c;
                indices[index++] = (short) b;
                indices[index++] = (short) d;
                indices[index++] = (short) c;
            }
        }
        Mesh mesh = mesh(vertexCount, indices.length);
        mesh.setVertices(vertices);
        mesh.setIndices(indices);
        return mesh;
    }

    private static Mesh buildOcean(WorldBounds bounds) {
        float minX = bounds.minX();
        float maxX = bounds.maxX();
        float minZ = -bounds.maxY();
        float maxZ = -bounds.minY();
        float[] vertices = {
                minX, 0f, minZ, 0.08f, 0.38f, 0.62f, 0.52f,
                maxX, 0f, minZ, 0.08f, 0.38f, 0.62f, 0.52f,
                minX, 0f, maxZ, 0.08f, 0.38f, 0.62f, 0.52f,
                maxX, 0f, maxZ, 0.08f, 0.38f, 0.62f, 0.52f
        };
        short[] indices = {0, 1, 2, 1, 3, 2};
        Mesh mesh = mesh(4, 6);
        mesh.setVertices(vertices);
        mesh.setIndices(indices);
        return mesh;
    }

    private static Mesh mesh(int vertices, int indices) {
        return new Mesh(
                true,
                vertices,
                indices,
                new VertexAttribute(VertexAttributes.Usage.Position, 3, ShaderProgram.POSITION_ATTRIBUTE),
                new VertexAttribute(VertexAttributes.Usage.ColorUnpacked, 4, ShaderProgram.COLOR_ATTRIBUTE));
    }

    private void updateCamera() {
        float centerX = (bounds.minX() + bounds.maxX()) * 0.5f;
        float centerZ = -(bounds.minY() + bounds.maxY()) * 0.5f;
        float targetY = cameraTargetY();
        float pitchRadians = pitch * MathUtils.degreesToRadians;
        float yawRadians = yaw * MathUtils.degreesToRadians;
        float horizontal = MathUtils.cos(pitchRadians) * distance;
        camera.position.set(
                centerX + MathUtils.cos(yawRadians) * horizontal,
                targetY + MathUtils.sin(pitchRadians) * distance,
                centerZ + MathUtils.sin(yawRadians) * horizontal);
        camera.up.set(Vector3.Y);
        camera.lookAt(centerX, targetY, centerZ);
        camera.update();
    }

    private float cameraTargetY() {
        float maximumLand = (float) elevationRange.maximumSubunits()
                / ElevationField.SUBUNITS_PER_CELL
                * VERTICAL_EXAGGERATION;
        return Math.max(0f, maximumLand * 0.28f);
    }

    private float cameraFarPlane() {
        if (generatedConfig == null) return 500f;
        float horizontal = generatedConfig.maxHorizontalDimension() * 8f;
        float vertical = Math.max(
                1f,
                (float) elevationRange.maximumSubunits() / ElevationField.SUBUNITS_PER_CELL)
                * VERTICAL_EXAGGERATION * 6f;
        return Math.max(500f, Math.max(horizontal, vertical));
    }

    private void fitCameraToWorld() {
        float horizontal = generatedConfig.maxHorizontalDimension() * 1.4f;
        float vertical = Math.max(
                0f,
                (float) elevationRange.maximumSubunits() / ElevationField.SUBUNITS_PER_CELL)
                * VERTICAL_EXAGGERATION * 1.8f;
        distance = Math.max(50f, Math.max(horizontal, vertical));
    }

    private void drawOverlay() {
        batch.begin();
        font.setColor(Color.WHITE);
        font.draw(
                batch,
                twoDimensional
                        ? "WORLD GENERATION / 2D SURFACE V15"
                        : "WORLD GENERATION / CONTINENTAL BASINS V15",
                24f,
                Gdx.graphics.getHeight() - 24f);
        font.draw(batch, String.format(
                "active %dx%d (%,d columns)   z %d..%d   shape overrides %,d   generation %.1f ms",
                generatedConfig.width(),
                generatedConfig.length(),
                generatedConfig.columnCount(),
                bounds.minZ(),
                bounds.maxZ(),
                generatedShapes.overrideCount(),
                generationMillis),
                24f,
                Gdx.graphics.getHeight() - 48f);
        font.draw(batch, String.format(
                "base: seed %d   land %.0f%%   scale %.0f%%   fragmentation %.0f%%   macro %.0f%%   local %.0f%%",
                generatedConfig.seed(),
                generatedConfig.coveragePpm() / 10_000f,
                generatedConfig.scalePpm() / 10_000f,
                generatedConfig.fragmentationPpm() / 10_000f,
                generatedConfig.reliefPpm() / 10_000f,
                generatedConfig.localReliefPpm() / 10_000f),
                24f,
                Gdx.graphics.getHeight() - 72f);
        font.draw(batch, String.format(
                "mountains: amount %.0f%%   height %.0f%%   scale %.0f%%   chain %.0f%%   sharp %.0f%%   plateau %s / %.0f%%",
                generatedConfig.mountainAbundancePpm() / 10_000f,
                generatedConfig.mountainHeightPpm() / 10_000f,
                generatedConfig.mountainScalePpm() / 10_000f,
                generatedConfig.mountainChaininessPpm() / 10_000f,
                generatedConfig.mountainSharpnessPpm() / 10_000f,
                generatedConfig.mountainPlateausEnabled() ? "on" : "off",
                generatedConfig.mountainPlateauProbabilityPpm() / 10_000f),
                24f,
                Gdx.graphics.getHeight() - 96f);
        if (twoDimensional) {
            font.draw(batch, String.format(
                    "FPS %d   frame %.1f ms   CPU %.1f ms   visible %,d   sampled %,d   LOD x%d",
                    Gdx.graphics.getFramesPerSecond(),
                    Gdx.graphics.getDeltaTime() * 1000f,
                    lastCpuMillis,
                    shape2DRenderer.visibleColumns(),
                    shape2DRenderer.renderedSamples(),
                    shape2DRenderer.lodStride()),
                    24f,
                    Gdx.graphics.getHeight() - 120f);
            if (showHydrologyDiagnostics && generatedHydrologyTopology != null) {
                font.setColor(Color.CYAN);
                font.draw(batch, hydrologySummary(), 24f, Gdx.graphics.getHeight() - 144f);
                font.setColor(Color.WHITE);
            }
        } else {
            font.draw(batch, String.format(
                    "FPS %d   frame %.1f ms   CPU %.1f ms   preview mesh %dx%d   chunks %d   axis cap %d",
                    Gdx.graphics.getFramesPerSecond(),
                    Gdx.graphics.getDeltaTime() * 1000f,
                    lastCpuMillis,
                    previewWidth,
                    previewLength,
                    surfaceMeshes.length,
                    WorldGeneration3DDetail.maxAxisSamples()),
                    24f,
                    Gdx.graphics.getHeight() - 120f);
        }

        font.setColor(Color.LIGHT_GRAY);
        if (twoDimensional) {
            font.draw(
                    batch,
                    "WASD / drag: pan | wheel: zoom (" + shape2DRenderer.zoomLabel()
                            + ") | F: fit | F3: shape directions | F4: hydrology topology "
                            + (showHydrologyDiagnostics ? "ON" : "OFF")
                            + " | Esc: development tools",
                    24f,
                    24f);
        } else {
            font.draw(
                    batch,
                    "drag: orbit | wheel: zoom | Esc: development tools | switch 2D/3D in the right panel",
                    24f,
                    24f);
        }
        batch.end();
    }

    private String hydrologySummary() {
        int boundary = 0;
        int routed = 0;
        int closed = 0;
        for (int bodyId = 0; bodyId < generatedHydrologyTopology.bodyCount(); bodyId++) {
            StandingWaterBoundaryRoute route = generatedHydrologyTopology.boundaryRoutes().route(bodyId);
            if (route.boundaryConnected()) {
                boundary++;
            } else if (route.reachesBoundaryWater()) {
                routed++;
            } else {
                closed++;
            }
        }
        return String.format(
                "F4 HYDROLOGY: bodies %d   boundary %d   routed %d   closed %d   spill links %d",
                generatedHydrologyTopology.bodyCount(),
                boundary,
                routed,
                closed,
                generatedHydrologyTopology.spills().connections().size());
    }

    private void disposeMeshes() {
        disposeSurfaceMeshes();
        if (oceanMesh != null) {
            oceanMesh.dispose();
            oceanMesh = null;
        }
    }

    private void disposeSurfaceMeshes() {
        for (Mesh surfaceMesh : surfaceMeshes) {
            surfaceMesh.dispose();
        }
        surfaceMeshes = new Mesh[0];
    }

    private static int sampleCoordinate(int min, int max, int sampleIndex, int sampleCount) {
        if (sampleCount <= 1) return min;
        long span = (long) max - min;
        return Math.toIntExact(min + span * sampleIndex / (sampleCount - 1L));
    }

    private final class PreviewInput extends InputAdapter {
        @Override
        public boolean keyDown(int keycode) {
            if (keycode == Input.Keys.ESCAPE) {
                returnToWorkspace.run();
                return true;
            }
            if (twoDimensional && keycode == Input.Keys.F) {
                shape2DRenderer.fitToWorld();
                hydrologyDiagnosticRenderer.fitToWorld();
                return true;
            }
            if (twoDimensional && keycode == Input.Keys.F3) {
                shape2DRenderer.toggleShapeDirections();
                return true;
            }
            if (twoDimensional && keycode == Input.Keys.F4) {
                showHydrologyDiagnostics = !showHydrologyDiagnostics;
                if (showHydrologyDiagnostics) ensureHydrologyTopology();
                return true;
            }
            return false;
        }

        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            if (button != Input.Buttons.LEFT
                    || settingsPanel.containsScreenPoint(screenX, screenY)) {
                return false;
            }
            lastMouseX = screenX;
            lastMouseY = screenY;
            if (twoDimensional) {
                panning2D = true;
            } else {
                orbiting = true;
            }
            return true;
        }

        @Override
        public boolean touchUp(int screenX, int screenY, int pointer, int button) {
            if (button != Input.Buttons.LEFT) return false;
            boolean handled = orbiting || panning2D;
            orbiting = false;
            panning2D = false;
            return handled;
        }

        @Override
        public boolean touchDragged(int screenX, int screenY, int pointer) {
            if (panning2D) {
                float deltaX = screenX - lastMouseX;
                float deltaY = screenY - lastMouseY;
                shape2DRenderer.panByPixels(deltaX, deltaY);
                hydrologyDiagnosticRenderer.panByPixels(deltaX, deltaY);
                lastMouseX = screenX;
                lastMouseY = screenY;
                return true;
            }
            if (!orbiting) return false;
            yaw += (screenX - lastMouseX) * 0.45f;
            pitch = MathUtils.clamp(pitch - (screenY - lastMouseY) * 0.35f, 8f, 82f);
            lastMouseX = screenX;
            lastMouseY = screenY;
            return true;
        }

        @Override
        public boolean scrolled(float amountX, float amountY) {
            if (settingsPanel.containsScreenPoint(Gdx.input.getX(), Gdx.input.getY())) {
                return false;
            }
            if (twoDimensional) {
                shape2DRenderer.zoom(amountY);
                hydrologyDiagnosticRenderer.zoom(amountY);
                return true;
            }
            float minDistance = Math.max(24f, generatedConfig.maxHorizontalDimension() * 0.35f);
            float maxDistance = Math.max(180f, generatedConfig.maxHorizontalDimension() * 4f);
            distance = MathUtils.clamp(distance * (1f + amountY * 0.08f), minDistance, maxDistance);
            return true;
        }
    }
}
