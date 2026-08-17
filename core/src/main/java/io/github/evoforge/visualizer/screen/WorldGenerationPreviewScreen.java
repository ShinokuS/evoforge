package io.github.evoforge.visualizer.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
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
import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.atlas.ElevationGenerationStage;
import io.github.evoforge.simulation.world.genesis.GenerationRevision;
import io.github.evoforge.simulation.world.genesis.RngRevision;
import io.github.evoforge.simulation.world.genesis.WorldGenerationIntent;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.genesis.WorldSpec;
import io.github.evoforge.simulation.world.spatial.WorldBounds;

/** Interactive 3D inspection tool for the current ocean-first macro world slice. */
public final class WorldGenerationPreviewScreen extends ScreenAdapter {
    private static final WorldBounds BOUNDS = new WorldBounds(-32, 31, -32, 31, -12, 12);
    private static final int STEP_PPM = 50_000;
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

    private final Runnable returnToMenu;
    private final PerspectiveCamera camera = new PerspectiveCamera();
    private final SpriteBatch batch = new SpriteBatch();
    private final BitmapFont font = new BitmapFont();
    private final ShaderProgram shader = new ShaderProgram(VERTEX_SHADER, FRAGMENT_SHADER);
    private final PreviewInput input = new PreviewInput();

    private Mesh surfaceMesh;
    private Mesh oceanMesh;
    private long seed = 1L;
    private int coveragePpm = 350_000;
    private int scalePpm = 750_000;
    private int fragmentationPpm = 250_000;
    private boolean showSurface = true;
    private boolean showOcean = true;
    private float yaw = 45f;
    private float pitch = 42f;
    private float distance = 86f;
    private int lastMouseX;
    private int lastMouseY;

    public WorldGenerationPreviewScreen(Runnable returnToMenu) {
        if (returnToMenu == null) throw new IllegalArgumentException("returnToMenu must not be null");
        if (!shader.isCompiled()) throw new IllegalStateException("world preview shader failed: " + shader.getLog());
        this.returnToMenu = returnToMenu;
        regenerate();
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(input);
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
    }

    @Override
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        camera.fieldOfView = 55f;
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.near = 0.1f;
        camera.far = 500f;
        camera.update();
        batch.getProjectionMatrix().setToOrtho2D(0f, 0f, width, height);
    }

    @Override
    public void hide() {
        if (Gdx.input.getInputProcessor() == input) Gdx.input.setInputProcessor(null);
    }

    @Override
    public void dispose() {
        hide();
        disposeMeshes();
        shader.dispose();
        batch.dispose();
        font.dispose();
    }

    private void regenerate() {
        WorldGenerationIntent intent = new WorldGenerationIntent(
                NormalizedValue.ofPartsPerMillion(coveragePpm),
                NormalizedValue.ofPartsPerMillion(scalePpm),
                NormalizedValue.ofPartsPerMillion(fragmentationPpm));
        WorldGenesis genesis = new WorldGenesis(
                new WorldSpec(BOUNDS), seed, GenerationRevision.V9, RngRevision.V1, intent);
        ElevationField elevation = new ElevationGenerationStage().generate(genesis);
        disposeMeshes();
        surfaceMesh = buildSurface(elevation);
        oceanMesh = buildOcean();
    }

    private static Mesh buildSurface(ElevationField elevation) {
        int width = BOUNDS.maxX() - BOUNDS.minX() + 1;
        int height = BOUNDS.maxY() - BOUNDS.minY() + 1;
        int vertexCount = width * height;
        float[] vertices = new float[vertexCount * 7];
        int cursor = 0;
        for (int y = BOUNDS.minY(); y <= BOUNDS.maxY(); y++) {
            for (int x = BOUNDS.minX(); x <= BOUNDS.maxX(); x++) {
                float h = (float) elevation.elevationSubunitsAt(x, y)
                        / ElevationField.SUBUNITS_PER_CELL;
                float normalized = MathUtils.clamp(Math.abs(h) / 12f, 0f, 1f);
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
        short[] indices = new short[(width - 1) * (height - 1) * 6];
        int index = 0;
        for (int y = 0; y < height - 1; y++) {
            for (int x = 0; x < width - 1; x++) {
                int a = y * width + x;
                int b = a + 1;
                int c = a + width;
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

    private static Mesh buildOcean() {
        float minX = BOUNDS.minX();
        float maxX = BOUNDS.maxX();
        float minY = BOUNDS.minY();
        float maxY = BOUNDS.maxY();
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
        float pitchRadians = pitch * MathUtils.degreesToRadians;
        float yawRadians = yaw * MathUtils.degreesToRadians;
        float horizontal = MathUtils.cos(pitchRadians) * distance;
        camera.position.set(
                MathUtils.cos(yawRadians) * horizontal,
                MathUtils.sin(pitchRadians) * distance,
                MathUtils.sin(yawRadians) * horizontal);
        camera.up.set(Vector3.Y);
        camera.lookAt(0f, 0f, 0f);
        camera.update();
    }

    private void drawOverlay() {
        batch.begin();
        font.setColor(Color.WHITE);
        font.draw(batch, "WORLD GENERATION / OCEAN-FIRST V9", 24f, Gdx.graphics.getHeight() - 24f);
        font.draw(batch, String.format(
                "seed %d   land %.0f%%   scale %.0f%%   fragmentation %.0f%%",
                seed, coveragePpm / 10_000f, scalePpm / 10_000f, fragmentationPpm / 10_000f),
                24f, Gdx.graphics.getHeight() - 48f);
        font.setColor(Color.LIGHT_GRAY);
        font.draw(batch,
                "drag: orbit | wheel: zoom | R: new seed | Left/Right: land | Up/Down: scale | PgUp/PgDn: fragmentation | T: surface | O: ocean | Esc: menu",
                24f, 28f);
        batch.end();
    }

    private void disposeMeshes() {
        if (surfaceMesh != null) { surfaceMesh.dispose(); surfaceMesh = null; }
        if (oceanMesh != null) { oceanMesh.dispose(); oceanMesh = null; }
    }

    private void adjust(IntentAxis axis, int delta) {
        switch (axis) {
            case COVERAGE -> coveragePpm = clampPpm(coveragePpm + delta);
            case SCALE -> scalePpm = clampPpm(scalePpm + delta);
            case FRAGMENTATION -> fragmentationPpm = clampPpm(fragmentationPpm + delta);
        }
        regenerate();
    }

    private static int clampPpm(int value) {
        return Math.max(0, Math.min(NormalizedValue.SCALE, value));
    }

    private enum IntentAxis { COVERAGE, SCALE, FRAGMENTATION }

    private final class PreviewInput extends InputAdapter {
        @Override
        public boolean keyDown(int keycode) {
            switch (keycode) {
                case Input.Keys.ESCAPE -> returnToMenu.run();
                case Input.Keys.R -> { seed++; regenerate(); }
                case Input.Keys.T -> showSurface = !showSurface;
                case Input.Keys.O -> showOcean = !showOcean;
                case Input.Keys.LEFT -> adjust(IntentAxis.COVERAGE, -STEP_PPM);
                case Input.Keys.RIGHT -> adjust(IntentAxis.COVERAGE, STEP_PPM);
                case Input.Keys.DOWN -> adjust(IntentAxis.SCALE, -STEP_PPM);
                case Input.Keys.UP -> adjust(IntentAxis.SCALE, STEP_PPM);
                case Input.Keys.PAGE_DOWN -> adjust(IntentAxis.FRAGMENTATION, -STEP_PPM);
                case Input.Keys.PAGE_UP -> adjust(IntentAxis.FRAGMENTATION, STEP_PPM);
                default -> { return false; }
            }
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
            distance = MathUtils.clamp(distance * (1f + amountY * 0.08f), 24f, 180f);
            return true;
        }
    }
}
