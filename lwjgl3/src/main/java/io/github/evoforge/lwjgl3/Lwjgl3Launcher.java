package io.github.evoforge.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import io.github.evoforge.Main;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Launches the desktop (LWJGL3) visualizer. */
public class Lwjgl3Launcher {
    public static void main(String[] args) {
        if (StartupHelper.startNewJvmIfRequired()) return;

        String sessionId = ensureSessionId();
        Logger logger = LoggerFactory.getLogger(Lwjgl3Launcher.class);
        Thread.setDefaultUncaughtExceptionHandler((thread, error) ->
                logger.atError()
                        .addKeyValue("event", "runtime.uncaught")
                        .addKeyValue("threadName", thread.getName())
                        .setCause(error)
                        .log("Uncaught runtime exception"));

        logger.atInfo()
                .addKeyValue("event", "runtime.start")
                .addKeyValue("sessionId", sessionId)
                .addKeyValue("java", System.getProperty("java.version"))
                .addKeyValue("os", System.getProperty("os.name"))
                .addKeyValue("arch", System.getProperty("os.arch"))
                .addKeyValue("logDir", System.getProperty("evoforge.log.dir", "logs"))
                .log("EvoForge runtime starting");

        createApplication();

        logger.atInfo()
                .addKeyValue("event", "runtime.stop")
                .log("EvoForge runtime stopped normally");
    }

    private static String ensureSessionId() {
        String configured = System.getProperty("evoforge.session");
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        String generated = UUID.randomUUID().toString();
        System.setProperty("evoforge.session", generated);
        return generated;
    }

    private static Lwjgl3Application createApplication() {
        return new Lwjgl3Application(new Main(), getDefaultConfiguration());
    }

    private static Lwjgl3ApplicationConfiguration getDefaultConfiguration() {
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle("EvoForge - Z-level Visualizer");
        configuration.useVsync(true);
        configuration.setForegroundFPS(
                Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate + 1);
        configuration.setWindowedMode(1100, 700);
        configuration.setWindowIcon(
                "libgdx128.png",
                "libgdx64.png",
                "libgdx32.png",
                "libgdx16.png");
        return configuration;
    }
}
