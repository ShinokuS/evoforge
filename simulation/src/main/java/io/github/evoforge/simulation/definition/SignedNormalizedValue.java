package io.github.evoforge.simulation.definition;

/**
 * Human-authored semantic coordinate on the closed interval {@code [-1, 1]}.
 *
 * <p>Negative and positive values express opposite directions of one human-readable quality. The
 * value itself carries no physical unit or implementation constant; generation/simulation logic
 * owns the later conversion into exact domain metrics.</p>
 */
public record SignedNormalizedValue(double value) {
    public SignedNormalizedValue {
        if (!Double.isFinite(value) || value < -1.0 || value > 1.0) {
            throw new IllegalArgumentException("signed normalized value must be finite and within -1..1");
        }
    }

    public static SignedNormalizedValue of(double value) {
        return new SignedNormalizedValue(value);
    }

    public static SignedNormalizedValue parse(String decimal) {
        if (decimal == null) {
            throw new IllegalArgumentException("signed normalized decimal must not be null");
        }
        try {
            return new SignedNormalizedValue(Double.parseDouble(decimal));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("signed normalized value must be a decimal within -1..1", exception);
        }
    }
}
