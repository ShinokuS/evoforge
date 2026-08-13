package io.github.evoforge;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import io.github.evoforge.visualizer.scenario.ScenarioCatalog;
import io.github.evoforge.visualizer.scenario.VisualizerScenario;
import io.github.evoforge.visualizer.screen.ScenarioMenuScreen;
import io.github.evoforge.visualizer.screen.ScenarioScreen;

/** Launches the scenario-driven simulation debug visualizer. */
public final class Main extends Game {

    private ScenarioCatalog scenarios;

    @Override
    public void create() {
        scenarios = ScenarioCatalog.standard();
        showScenarioMenuNow();
    }

    @Override
    public void dispose() {
        Screen current = getScreen();
        if (current != null) {
            current.dispose();
        }
    }

    private void requestScenario(VisualizerScenario scenario) {
        Gdx.app.postRunnable(() -> showScenarioNow(scenario));
    }

    private void requestScenarioMenu() {
        Gdx.app.postRunnable(this::showScenarioMenuNow);
    }

    private void showScenarioMenuNow() {
        replaceScreen(new ScenarioMenuScreen(
                scenarios,
                this::requestScenario));
    }

    private void showScenarioNow(VisualizerScenario scenario) {
        replaceScreen(new ScenarioScreen(
                scenario,
                () -> requestScenario(scenario),
                this::requestScenarioMenu));
    }

    private void replaceScreen(Screen next) {
        Screen previous = getScreen();
        setScreen(next);
        if (previous != null) {
            previous.dispose();
        }
    }
}
