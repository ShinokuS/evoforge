package io.github.evoforge.simulation.world.geology;

import java.util.regex.Pattern;

/** Stable content material identity associated with a generated geological unit. */
public record GeologyMaterialKey(String value) {
    private static final Pattern KEY = Pattern.compile(
            "[a-z0-9][a-z0-9._-]*:[a-z0-9][a-z0-9._/-]*");

    public GeologyMaterialKey {
        if (value == null || !KEY.matcher(value).matches()) {
            throw new IllegalArgumentException("invalid geology material key: " + value);
        }
    }

    public static GeologyMaterialKey of(String value) {
        return new GeologyMaterialKey(value);
    }
}
