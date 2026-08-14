package io.github.evoforge.visualizer.presentation.weather;

/** Immutable presentation-only weather state. Simulation weather can adapt to this later. */
public record WeatherPresentation(
        WeatherPresentationKind kind,
        float intensity) {

    public static final WeatherPresentation CLEAR =
            new WeatherPresentation(WeatherPresentationKind.CLEAR, 0f);

    public WeatherPresentation {
        if (kind == null) {
            throw new IllegalArgumentException("weather kind must not be null");
        }
        if (!Float.isFinite(intensity) || intensity < 0f || intensity > 1f) {
            throw new IllegalArgumentException(
                    "weather intensity must be finite and in [0, 1]");
        }
        if (kind == WeatherPresentationKind.CLEAR && intensity != 0f) {
            throw new IllegalArgumentException(
                    "clear weather must have zero intensity");
        }
    }

    public static WeatherPresentation rain(float intensity) {
        if (intensity <= 0f) {
            throw new IllegalArgumentException(
                    "rain intensity must be > 0");
        }
        return new WeatherPresentation(
                WeatherPresentationKind.RAIN,
                intensity);
    }
}
