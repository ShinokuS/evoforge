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
import io.github.evoforge.simulation.world.genesis.GenerationRevision;
import io.github.evoforge.simulation.world.genesis.RngRevision;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.genesis.WorldSpec;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import io.github.evoforge.simulation.world.terrain.shape.TerrainShapeField;
import io.github.evoforge.simulation.world.terrain.shape.TerrainShapeGenerationStage;

/** Interactive 2D/3D inspection workspace for macro morphology and generated surface geometry. */
public final class WorldGenerationPreviewScreen extends ScreenAdapter {
    private static final int MAX_PREVIEW_AXIS = 160;
    private static final float VERTICAL_EXAGGERATION = 1.35f;

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
    private final WorldGenerationSettingsPanel settingsPanel;
    private final InputMultiplexer inputMultiplexer;

    private WorldGenerationPreviewConfig generatedConfig;
    private WorldBounds bounds;
    private ElevationField generatedElevation;
    private TerrainShapeField generatedShapes;
    private Mesh surfaceMesh;
    private Mesh oceanMesh;
    private boolean showSurface = true;
    private boolean showOcean = true;
    private boolean twoDimensional;
    private int elevationTintPpm = WorldGenerationElevationTint.DEFAULT_STRENGTH_PPM;
    private float yaw = 45f;
    private float pitch = 42f;
    private float distance = 86f;
    private int previewWidth;
    private int previewLength;
    private double generationMillis;
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
                this::setElevationTintPpm);
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
        Gdx.gl.glClearColor(0.025f, 0.035f, 0.05f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        if (twoDimensional) {
            shape2DRenderer.update(delta, !settingsPanel.keyboardInputActive());
            renderTwoDimensional();
        } else {
            renderThreeDimensional();
        }
        drawOverlay();
        settingsPanel.render(delta);
    }

    private void renderThreeDimensional() {
        updateCamera();
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        shader.bind();
        shader.setUniformMatrix("u_projView", camera.combined);
        if (showSurface && surfaceMesh != null) surfaceMesh.render(shader, GL20.GL_TRIANGLES);
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
    }

    @Override
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        camera.fieldOfView = 55f;
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.near = 0.1f;
        camera.far = Math.max(500f, generatedConfig.maxHorizontalDimension() * 8f);
        camera.update();
        batch.getProjectionMatrix().setToOrtho2D(0f, 0f, width, height);
        settingsPanel.resize(width, height);
        shape2DRenderer.resize(
                Math.max(1, Math.round(settingsPanel.previewRightEdge())),
                height);
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
                GenerationRevision.V10,
                RngRevision.V1,
                generatedConfig.intent());

        long started = System.nanoTime();
        generatedElevation = new ElevationGenerationStage().generate(genesis);
        generatedShapes = TerrainShapeGenerationStage.standard().generate(generatedElevation);
        generationMillis = (System.nanoTime() - started) / 1_000_000d;

        disposeMeshes();
        previewWidth = sampleCount(generatedConfig.width());
        previewLength = sampleCount(generatedConfig.length());
        surfaceMesh = buildSurface(
                generatedElevation,
                bounds,
                previewWidth,
                previewLength,
                elevationTintPpm);
        oceanMesh = buildOcean(bounds);
        shape2DRenderer.setWorldBounds(bounds);
        shape2DRenderer.setElevationTintPpm(elevationTintPpm);
        camera.far = Math.max(500f, generatedConfig.maxHorizontalDimension() * 8f);

        if (previous == null
                || previous.width() != generatedConfig.width()
                || previous.length() != generatedConfig.length()) {
            fitCameraToWorld();
            shape2DRenderer.fitToWorld();
        }
    }

    private void setElevationTintPpm(int strengthPpm) {
        if (strengthPpm < 0 || strengthPpm > WorldGenerationElevationTint.SCALE) {
            throw new IllegalArgumentException("elevation color sensitivity must be normalized ppm");
        }
        if (elevationTintPpm == strengthPpm) return;
        elevationTintPpm = strengthPpm;
        shape2DRenderer.setElevationTintPpm(strengthPpm);
        if (generatedElevation == null || bounds == null) return;
        if (surfaceMesh != null) surfaceMesh.dispose();
        surfaceMesh = buildSurface(
                generatedElevation,
                bounds,
                previewWidth,
                previewLength,
                elevationTintPpm);
    }

    private static Mesh buildSurface(
            ElevationField elevation,
            WorldBounds bounds,
            int sampleWidth,
            int sampleLength,
            int elevationTintPpm) {
        int vertexCount = Math.multiplyExact(sampleWidth, sampleLength);
        float[] vertices = new float[vertexCount * 7];
        Color color = new Color();
        int cursor = 0;
        for (int sampleY = 0; sampleY < sampleLength; sampleY++) {
            int y = sampleCoordinate(bounds.minY(), bounds.maxY(), sampleY, sampleLength);
            for (int sampleX = 0; sampleX < sampleWidth; sampleX++) {
                int x = sampleCoordinate(bounds.minX(), bounds.maxX(), sampleX, sampleWidth);
                long heightSubunits = elevation.elevationSubunitsAt(x, y);
                float h = (float) heightSubunits / ElevationField.SUBUNITS_PER_CELL;
                if (heightSubunits > 0L) {
                    WorldGenerationElevationTint.color(
                            heightSubunits,
                            bounds,
                            elevationTintPpm,
                            color);
                } else {
                    color.set(0.16f, 0.24f, 0.30f, 1f);
                }
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
        float pitchRadians = pitch * MathUtils.degreesToRadians;
        float yawRadians = yaw * MathUtils.degreesToRadians;
        float horizontal = MathUtils.cos(pitchRadians) * distance;
        camera.position.set(
                centerX + MathUtils.cos(yawRadians) * horizontal,
                MathUtils.sin(pitchRadians) * distance,
                centerZ + MathUtils.sin(yawRadians) * horizontal);
        camera.up.set(Vector3.Y);
        camera.lookAt(centerX, 0f, centerZ);
        camera.update();
    }

    private void fitCameraToWorld() {
        distance = Math.max(50f, generatedConfig.maxHorizontalDimension() * 1.4f);
    }

    private void drawOverlay() {
        batch.begin();
        font.setColor(Color.WHITE);
        font.draw(
                batch,
                twoDimensional
                        ? "WORLD GENERATION / 2D SURFACE"
                        : "WORLD GENERATION / MACRO MORPHOLOGY V10",
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
                "active seed %d   land %.0f%%   scale %.0f%%   fragmentation %.0f%%   relief %.0f%%",
                generatedConfig.seed(),
                generatedConfig.coveragePpm() / 10_000f,
                generatedConfig.scalePpm() / 10_000f,
                generatedConfig.fragmentationPpm() / 10_000f,
                generatedConfig.reliefPpm() / 10_000f),
                24f,
                Gdx.graphics.getHeight() - 72f);
        font.setColor(Color.LIGHT_GRAY);
        if (twoDimensional) {
            font.draw(
                    batch,
                    "WASD / drag: pan | wheel: zoom (" + shape2DRenderer.zoomLabel()
                            + ") | F: fit | F3: shape directions | Esc: development tools",
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

    private void disposeMeshes() {
        if (surfaceMesh != null) {
            surfaceMesh.dispose();
            surfaceMesh = null;
        }
        if (oceanMesh != null) {
            oceanMesh.dispose();
            oceanMesh = null;
        }
    }

    private static int sampleCount(int dimension) {
        return Math.min(dimension, MAX_PREVIEW_AXIS);
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
                return true;
            }
            if (twoDimensional && keycode == Input.Keys.F3) {
                shape2DRenderer.toggleShapeDirections();
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
                shape2DRenderer.panByPixels(
                        screenX - lastMouseX,
                        screenY - lastMouseY);
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
                return true;
            }
            float minDistance = Math.max(24f, generatedConfig.maxHorizontalDimension() * 0.35f);
            float maxDistance = Math.max(180f, generatedConfig.maxHorizontalDimension() * 4f);
            distance = MathUtils.clamp(distance * (1f + amountY * 0.08f), minDistance, maxDistance);
            return true;
        }
    }
}
