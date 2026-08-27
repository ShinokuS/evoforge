package io.github.evoforge.simulation.definition;

/**
 * Human-authored semantic coordinate on the closed interval {@code [0, 1]}.
 *
 * <p>The current representation remains a double for compatibility with the refactored simulation,
 * while the historical V12-V15 generator can recover its exact authored parts-per-million contract.
 * Values created through {@link #ofPartsPerMillion(int)} round-trip exactly through
 * {@link #partsPerMillion()}.</p>
 */
public record NormalizedValue(double value) {
    public static final int SCALE = 1_000_000;

    public NormalizedValue {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException("normalized value must be finite and within 0..1");
        }
    }

    public static NormalizedValue of(double value) {
        return new NormalizedValue(value);
    }

    public static NormalizedValue ofPartsPerMillion(int partsPerMillion) {
        if (partsPerMillion < 0 || partsPerMillion > SCALE) {
            throw new IllegalArgumentException(
                    "normalized value must be within 0..1_000_000 parts per million");
        }
        return new NormalizedValue(partsPerMillion / (double) SCALE);
    }

    public int partsPerMillion() {
        return Math.toIntExact(Math.round(value * SCALE));
    }

    public static NormalizedValue parse(String decimal) {
        if (decimal == null) {
            throw new IllegalArgumentException("normalized decimal must not be null");
        }
        try {
            return new NormalizedValue(Double.parseDouble(decimal));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("normalized value must be a decimal within 0..1", exception);
        }
    }
}
