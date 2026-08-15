package io.github.evoforge.logging;

import com.badlogic.gdx.ApplicationLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Routes libGDX logging through the same runtime logging backend as EvoForge. */
public final class Slf4jApplicationLogger implements ApplicationLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger("libgdx");

    @Override
    public void log(String tag, String message) {
        LOGGER.atInfo()
                .addKeyValue("event", "gdx.log")
                .addKeyValue("tag", tag)
                .log(message);
    }

    @Override
    public void log(String tag, String message, Throwable exception) {
        LOGGER.atInfo()
                .addKeyValue("event", "gdx.log")
                .addKeyValue("tag", tag)
                .setCause(exception)
                .log(message);
    }

    @Override
    public void error(String tag, String message) {
        LOGGER.atError()
                .addKeyValue("event", "gdx.error")
                .addKeyValue("tag", tag)
                .log(message);
    }

    @Override
    public void error(String tag, String message, Throwable exception) {
        LOGGER.atError()
                .addKeyValue("event", "gdx.error")
                .addKeyValue("tag", tag)
                .setCause(exception)
                .log(message);
    }

    @Override
    public void debug(String tag, String message) {
        LOGGER.atDebug()
                .addKeyValue("event", "gdx.debug")
                .addKeyValue("tag", tag)
                .log(message);
    }

    @Override
    public void debug(String tag, String message, Throwable exception) {
        LOGGER.atDebug()
                .addKeyValue("event", "gdx.debug")
                .addKeyValue("tag", tag)
                .setCause(exception)
                .log(message);
    }
}
