package io.github.evoforge.simulation.definition;

import java.math.BigDecimal;

/**
 * Exact authored coordinate on the closed normalized interval {@code [0, 1]}.
 *
 * <p>JSON may remain convenient decimal notation, while domain code stores an exact fixed-point
 * value. The coordinate is semantic: it expresses relative authored intent and carries no physical
 * unit by itself.</p>
 */
public record NormalizedValue(int partsPerMillion) {
    public static final int SCALE = 1_000_000;

    public NormalizedValue {
        if (partsPerMillion < 0 || partsPerMillion > SCALE) {
            throw new IllegalArgumentException(
                    "normalized value must be within 0..1_000_000 parts per million");
        }
    }

    public static NormalizedValue ofPartsPerMillion(int partsPerMillion) {
        return new NormalizedValue(partsPerMillion);
    }

    /** Parses an authored decimal exactly; values needing more than six decimal places are rejected. */
    public static NormalizedValue parse(String decimal) {
        if (decimal == null) {
            throw new IllegalArgumentException("normalized decimal must not be null");
        }
        try {
            BigDecimal value = new BigDecimal(decimal);
            int partsPerMillion = value.movePointRight(6).intValueExact();
            return new NormalizedValue(partsPerMillion);
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "normalized value must be an exact decimal within 0..1 with at most six decimal places",
                    exception);
        }
    }
}
