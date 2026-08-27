package io.github.evoforge.simulation.world.genesis;

import java.util.regex.Pattern;

final class GenesisKeyFormat {
    private static final Pattern PATTERN = Pattern.compile(
            "[a-z0-9][a-z0-9_.-]*:[a-z0-9][a-z0-9_.-]*");

    private GenesisKeyFormat() {}

    static String requireKey(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        if (!PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("invalid " + label + ": " + value);
        }
        return value;
    }
}
