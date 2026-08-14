package io.github.evoforge.visualizer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import io.github.evoforge.simulation.runtime.SimulationView;
import io.github.evoforge.simulation.world.object.ObjectId;

/** Converts physical input into presentation state, camera and time controls. */
public final class VisualizerInputController extends InputAdapter {

    private final SimulationView view;
    private final VisualizerState state;
    private final VisualizerCamera camera;
    private final VisualizerTimeController time;

    public VisualizerInputController(
            SimulationView view,
            VisualizerState state,
            VisualizerCamera camera,
            VisualizerTimeController time) {
        this.view = require(view, "view");
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
            default -> { return false; }
        }
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        camera.zoom(amountY);
        return true;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        if (button != Input.Buttons.LEFT) return false;

        VisualizerCamera.Cell cell = camera.cellAt(screenX, screenY);
        int z = state.selectedZ();
        int count = view.cells().objectCount(cell.x(), cell.y(), z);
        ObjectId objectId = null;
        if (count > 0) {
            int selectedIndex = -1;
            VisualizerState.CellSelection selectedCell = state.selectedCell();
            ObjectId selectedObject = state.selectedObject();
            if (selectedCell != null && selectedCell.x() == cell.x() && selectedCell.y() == cell.y()
                    && selectedCell.z() == z && selectedObject != null) {
                for (int index = 0; index < count; index++) {
                    if (selectedObject.equals(view.cells().objectAt(cell.x(), cell.y(), z, index))) {
                        selectedIndex = index;
                        break;
                    }
                }
            }
            objectId = view.cells().objectAt(cell.x(), cell.y(), z, (selectedIndex + 1) % count);
        }
        state.selectCell(cell.x(), cell.y(), objectId);
        return true;
    }

    private static <T> T require(T value, String name) {
        if (value == null) throw new IllegalArgumentException(name + " must not be null");
        return value;
    }
}
