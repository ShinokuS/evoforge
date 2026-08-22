package io.github.evoforge.simulation.world.continuum.query;

/** Raised when a local query targets a world revision that is no longer current. */
public final class StaleContinuumQueryException extends IllegalStateException {
    public StaleContinuumQueryException(String message) {
        super(message);
    }
}
