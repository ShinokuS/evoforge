package io.github.evoforge.simulation.world.continuum.query;

import io.github.evoforge.simulation.world.continuum.field.ContinuumSampleWindow;

/** One consumer asking for one bounded local window of the Continuum. */
public record ContinuumLocalQueryRequest(String consumerId, ContinuumSampleWindow window, long revision) {
    public ContinuumLocalQueryRequest {
        if (consumerId == null || consumerId.isBlank()) {
            throw new IllegalArgumentException("consumerId must not be blank");
        }
        if (window == null) {
            throw new IllegalArgumentException("window must not be null");
        }
        if (revision < 0L) {
            throw new IllegalArgumentException("revision must be >= 0");
        }
    }
}
