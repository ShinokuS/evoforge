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

/** Interactive 3D inspection workspace for the current ocean-first macro world slice. */
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
    private final WorldGenerationSettingsPanel settingsPanel;
    private final InputMultiplexer inputMultiplexer;

    private WorldGenerationPreviewConfig generatedConfig;
    private WorldBounds bounds;
    private Mesh surfaceMesh;
    private Mesh oceanMesh;
    private boolean showSurface = true;
    private boolean showOcean = true;
    private float yaw = 45f;
    private float pitch = 42f;
    private float distance = 86f;
    private int previewWidth;
    private int previewHeight;
    private double generationMillis;
    private int lastMouseX;
    private int lastMouseY;

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
                visible -> showSurface = visible,
                visible -> showOcean = visible);
        this.inputMultiplexer = new InputMultiplexer(settingsPanel.inputProcessor(), input);
        regenerate();
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(inputMultiplexer);
    }

    @Override
    public void render(float delta) {
        updateCamera();
        Gdx.gl.glClearColor(0.025f, 0.035f, 0.05f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
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
        drawOverlay();
        settingsPanel.render(delta);
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
    }

    @Override
    public void hide() {
        if (Gdx.input.getInputProcessor() == inputMultiplexer) {
            Gdx.input.setInputProcessor(null);
        }
    }

    @Override
    public void dispose() {
        hide();
        disposeMeshes();
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
        ElevationField elevation = new ElevationGenerationStage().generate(genesis);
        generationMillis = (System.nanoTime() - started) / 1_000_000d;

        disposeMeshes();
        previewWidth = sampleCount(generatedConfig.width());
        previewHeight = sampleCount(generatedConfig.height());
        surfaceMesh = buildSurface(elevation, bounds, previewWidth, previewHeight);
        oceanMesh = buildOcean(bounds);
        camera.far = Math.max(500f, generatedConfig.maxHorizontalDimension() * 8f);

        if (previous == null
                || previous.width() != generatedConfig.width()
                || previous.height() != generatedConfig.height()) {
            fitCameraToWorld();
        }
    }

    private static Mesh buildSurface(
            ElevationField elevation,
            WorldBounds bounds,
            int sampleWidth,
            int sampleHeight) {
        int vertexCount = Math.multiplyExact(sampleWidth, sampleHeight);
        float[] vertices = new float[vertexCount * 7];
        float amplitude = Math.max(Math.abs(bounds.minZ()), Math.abs(bounds.maxZ()));
        int cursor = 0;
        for (int sampleY = 0; sampleY < sampleHeight; sampleY++) {
            int y = sampleCoordinate(bounds.minY(), bounds.maxY(), sampleY, sampleHeight);
            for (int sampleX = 0; sampleX < sampleWidth; sampleX++) {
                int x = sampleCoordinate(bounds.minX(), bounds.maxX(), sampleX, sampleWidth);
                float h = (float) elevation.elevationSubunitsAt(x, y)
                        / ElevationField.SUBUNITS_PER_CELL;
                float normalized = MathUtils.clamp(Math.abs(h) / Math.max(1f, amplitude), 0f, 1f);
                Color color = h > 0f
                        ? new Color(0.24f + normalized * 0.25f, 0.42f - normalized * 0.12f, 0.18f, 1f)
                        : new Color(0.16f, 0.20f + normalized * 0.10f, 0.24f + normalized * 0.10f, 1f);
                vertices[cursor++] = x;
                vertices[cursor++] = h * VERTICAL_EXAGGERATION;
                vertices[cursor++] = y;
                vertices[cursor++] = color.r;
                vertices[cursor++] = color.g;
                vertices[cursor++] = color.b;
                vertices[cursor++] = color.a;
            }
        }

        short[] indices = new short[(sampleWidth - 1) * (sampleHeight - 1) * 6];
        int index = 0;
        for (int y = 0; y < sampleHeight - 1; y++) {
            for (int x = 0; x < sampleWidth - 1; x++) {
                int a = y * sampleWidth + x;
                int b = a + 1;
                int c = a + sampleWidth;
                int d = c + 1;
                indices[index++] = (short) a;
                indices[index++] = (short) c;
                indices[index++] = (short) b;
                indices[index++] = (short) b;
                indices[index++] = (short) c;
                indices[index++] = (short) d;
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
        float minY = bounds.minY();
        float maxY = bounds.maxY();
        float[] vertices = {
                minX, 0f, minY, 0.08f, 0.38f, 0.62f, 0.52f,
                maxX, 0f, minY, 0.08f, 0.38f, 0.62f, 0.52f,
                minX, 0f, maxY, 0.08f, 0.38f, 0.62f, 0.52f,
                maxX, 0f, maxY, 0.08f, 0.38f, 0.62f, 0.52f
        };
        short[] indices = {0, 2, 1, 1, 2, 3};
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
        float centerY = (bounds.minY() + bounds.maxY()) * 0.5f;
        float pitchRadians = pitch * MathUtils.degreesToRadians;
        float yawRadians = yaw * MathUtils.degreesToRadians;
        float horizontal = MathUtils.cos(pitchRadians) * distance;
        camera.position.set(
                centerX + MathUtils.cos(yawRadians) * horizontal,
                MathUtils.sin(pitchRadians) * distance,
                centerY + MathUtils.sin(yawRadians) * horizontal);
        camera.up.set(Vector3.Y);
        camera.lookAt(centerX, 0f, centerY);
        camera.update();
    }

    private void fitCameraToWorld() {
        distance = Math.max(50f, generatedConfig.maxHorizontalDimension() * 1.4f);
    }

    private void drawOverlay() {
        batch.begin();
        font.setColor(Color.WHITE);
        font.draw(batch, "WORLD GENERATION / MACRO MORPHOLOGY V10", 24f, Gdx.graphics.getHeight() - 24f);
        font.draw(batch, String.format(
                "active %dx%d (%,d columns)   z %d..%d   preview %dx%d   generation %.1f ms",
                generatedConfig.width(),
                generatedConfig.height(),
                generatedConfig.columnCount(),
                bounds.minZ(),
                bounds.maxZ(),
                previewWidth,
                previewHeight,
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
        font.draw(batch,
                "drag: orbit | wheel: zoom | Esc: development tools | edit generation settings in the right panel",
                24f,
                24f);
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
            if (keycode != Input.Keys.ESCAPE) return false;
            returnToWorkspace.run();
            return true;
        }

        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            if (button != Input.Buttons.LEFT) return false;
            lastMouseX = screenX;
            lastMouseY = screenY;
            return true;
        }

        @Override
        public boolean touchDragged(int screenX, int screenY, int pointer) {
            yaw += (screenX - lastMouseX) * 0.45f;
            pitch = MathUtils.clamp(pitch - (screenY - lastMouseY) * 0.35f, 8f, 82f);
            lastMouseX = screenX;
            lastMouseY = screenY;
            return true;
        }

        @Override
        public boolean scrolled(float amountX, float amountY) {
            float minDistance = Math.max(24f, generatedConfig.maxHorizontalDimension() * 0.35f);
            float maxDistance = Math.max(180f, generatedConfig.maxHorizontalDimension() * 4f);
            distance = MathUtils.clamp(distance * (1f + amountY * 0.08f), minDistance, maxDistance);
            return true;
        }
    }
}
