package io.github.evoforge.simulation.definition;

/**
 * Human-authored semantic coordinate on the closed interval {@code [0, 1]}.
 *
 * <p>The value deliberately has no physical unit, fixed-point scale, cell count, threshold or
 * simulation constant attached to it. A domain compiler may later translate this intent into the
 * exact representation required by generation or simulation logic.</p>
 */
public record NormalizedValue(double value) {
    public NormalizedValue {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException("normalized value must be finite and within 0..1");
        }
    }

    public static NormalizedValue of(double value) {
        return new NormalizedValue(value);
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
