package io.github.evoforge.visualizer.screen;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import io.github.evoforge.visualizer.VisualizerCamera;
import io.github.evoforge.visualizer.ZLevelVisualizer;
import io.github.evoforge.visualizer.scenario.ScenarioSession;

final class ScenarioCellInput extends InputAdapter {

    private final ScenarioSession session;
    private final ZLevelVisualizer visualizer;

    ScenarioCellInput(ScenarioSession session, ZLevelVisualizer visualizer) {
        this.session = session;
        this.visualizer = visualizer;
    }

    @Override
    public boolean touchDown(
            int screenX,
            int screenY,
            int pointer,
            int button) {
        if (button != Input.Buttons.LEFT && button != Input.Buttons.RIGHT) {
            return false;
        }
        VisualizerCamera.Cell cell = visualizer.cellAt(screenX, screenY);
        int z = visualizer.selectedZ();
        if (button == Input.Buttons.LEFT) {
            session.controller().primaryCellAction(cell.x(), cell.y(), z);
            return false;
        }
        return session.controller().secondaryCellAction(cell.x(), cell.y(), z);
    }
}
