package io.github.evoforge;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.Version;
import com.badlogic.gdx.graphics.GL20;
import io.github.evoforge.logging.Slf4jApplicationLogger;
import io.github.evoforge.visualizer.scenario.ScenarioCatalog;
import io.github.evoforge.visualizer.scenario.VisualizerScenario;
import io.github.evoforge.visualizer.screen.ScenarioMenuScreen;
import io.github.evoforge.visualizer.screen.ScenarioScreen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Launches the EvoForge development visualizer. */
public final class Main extends Game {

    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    private ScenarioCatalog scenarios;

    @Override
    public void create() {
        Gdx.app.setApplicationLogger(new Slf4jApplicationLogger());
        Gdx.app.setLogLevel(Application.LOG_DEBUG);

        LOGGER.atInfo()
                .addKeyValue("event", "app.ready")
                .addKeyValue("libgdx", Version.VERSION)
                .addKeyValue("backend", Gdx.app.getType())
                .addKeyValue("windowWidth", Gdx.graphics.getWidth())
                .addKeyValue("windowHeight", Gdx.graphics.getHeight())
                .addKeyValue("glVendor", Gdx.gl.glGetString(GL20.GL_VENDOR))
                .addKeyValue("glRenderer", Gdx.gl.glGetString(GL20.GL_RENDERER))
                .addKeyValue("glVersion", Gdx.gl.glGetString(GL20.GL_VERSION))
                .log("EvoForge application ready");

        scenarios = ScenarioCatalog.standard();
        showScenarioMenuNow();
    }

    @Override
    public void dispose() {
        LOGGER.atInfo()
                .addKeyValue("event", "app.dispose")
                .log("Disposing EvoForge application");
        Screen current = getScreen();
        if (current != null) current.dispose();
    }

    private void requestScenarioMenu() {
        Gdx.app.postRunnable(this::showScenarioMenuNow);
    }

    private void requestScenario(VisualizerScenario scenario) {
        Gdx.app.postRunnable(() -> showScenarioNow(scenario));
    }

    private void requestExit() {
        Gdx.app.postRunnable(Gdx.app::exit);
    }

    private void showScenarioMenuNow() {
        replaceScreen(new ScenarioMenuScreen(
                scenarios,
                this::requestScenario,
                this::requestExit));
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
        if (previous != null) previous.dispose();
    }
}
