package io.github.evoforge.visualizer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;

/** Converts keyboard/mouse navigation into presentation state, camera and time controls. */
public final class VisualizerInputController extends InputAdapter {

    private final VisualizerState state;
    private final VisualizerCamera camera;
    private final VisualizerTimeController time;

    public VisualizerInputController(
            VisualizerState state,
            VisualizerCamera camera,
            VisualizerTimeController time) {
        this.state = require(state, "state");
        this.camera = require(camera, "camera");
        this.time = require(time, "time");
    }

    public void update(float deltaSeconds) {
        int x = 0;
        int y = 0;
        if (Gdx.input.isKeyPressed(Input.Keys.A)) x--;
        if (Gdx.input.isKeyPressed(Input.Keys.D)) x++;
        if (Gdx.input.isKeyPressed(Input.Keys.S)) y--;
        if (Gdx.input.isKeyPressed(Input.Keys.W)) y++;
        if (x != 0 || y != 0) camera.pan(x, y, deltaSeconds);
    }

    @Override
    public boolean keyDown(int keycode) {
        switch (keycode) {
            case Input.Keys.SPACE -> { time.toggleRunning(); return true; }
            case Input.Keys.N -> { if (!time.running()) time.stepOnce(); return true; }
            case Input.Keys.PAGE_UP -> { state.selectZ(1); return true; }
            case Input.Keys.PAGE_DOWN -> { state.selectZ(-1); return true; }
            case Input.Keys.G -> { state.cycleGridMode(); return true; }
            case Input.Keys.F2 -> { state.toggleTransitions(); return true; }
            case Input.Keys.F3 -> { state.toggleShapeDirections(); return true; }
            case Input.Keys.F4 -> { state.cycleLowerDepth(); return true; }
            case Input.Keys.F5 -> { state.toggleOccupancy(); return true; }
            case Input.Keys.F6 -> { state.toggleTechnicalDetails(); return true; }
            case Input.Keys.F7 -> { state.toggleDebugSlice(); return true; }
            default -> { return false; }
        }
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        camera.zoom(amountY);
        return true;
    }

    private static <T> T require(T value, String name) {
        if (value == null) throw new IllegalArgumentException(name + " must not be null");
        return value;
    }
}
