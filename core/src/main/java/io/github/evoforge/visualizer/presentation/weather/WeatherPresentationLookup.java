package io.github.evoforge.visualizer.presentation.weather;

/** Read-only presentation weather source; implementations may become dynamic later. */
@FunctionalInterface
public interface WeatherPresentationLookup {

    WeatherPresentation CLEAR = WeatherPresentation.CLEAR;
    WeatherPresentationLookup CLEAR_LOOKUP = () -> CLEAR;

    WeatherPresentation current();

    static WeatherPresentationLookup fixed(
            WeatherPresentation weather) {

        if (weather == null) {
            throw new IllegalArgumentException(
                    "weather must not be null");
        }
        return () -> weather;
    }
}
