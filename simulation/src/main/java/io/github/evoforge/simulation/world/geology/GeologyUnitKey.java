package io.github.evoforge.simulation.world.geology;

import java.util.regex.Pattern;

/** Stable authored identity of one geological unit; never a runtime definition id. */
public record GeologyUnitKey(String value) implements Comparable<GeologyUnitKey> {
    private static final Pattern KEY = Pattern.compile(
            "[a-z0-9][a-z0-9._-]*:[a-z0-9][a-z0-9._/-]*");

    public GeologyUnitKey {
        if (value == null || !KEY.matcher(value).matches()) {
            throw new IllegalArgumentException("invalid geology unit key: " + value);
        }
    }

    public static GeologyUnitKey of(String value) {
        return new GeologyUnitKey(value);
    }

    @Override
    public int compareTo(GeologyUnitKey other) {
        return value.compareTo(other.value);
    }
}
