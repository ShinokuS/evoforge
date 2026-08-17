package io.github.evoforge.simulation.world.bootstrap;

import io.github.evoforge.simulation.world.environment.atmosphere.AtmosphericWaterForcing;
import io.github.evoforge.simulation.world.weather.WeatherState;
import java.util.Optional;

/** Runtime-only atmosphere objects created from already prepared immutable world data. */
public record AtmosphericRuntimeComposition(
        Optional<AtmosphericWaterForcing> waterForcing,
        Optional<WeatherState> weatherState) {

    public AtmosphericRuntimeComposition {
        if (waterForcing == null || weatherState == null) {
            throw new IllegalArgumentException("atmospheric runtime composition optionals must not be null");
        }
    }

    public static AtmosphericRuntimeComposition disabled() {
        return new AtmosphericRuntimeComposition(Optional.empty(), Optional.empty());
    }

    public static AtmosphericRuntimeComposition forcing(AtmosphericWaterForcing forcing) {
        if (forcing == null) throw new IllegalArgumentException("forcing must not be null");
        return new AtmosphericRuntimeComposition(Optional.of(forcing), Optional.empty());
    }

    public static AtmosphericRuntimeComposition weather(
            AtmosphericWaterForcing forcing,
            WeatherState weather) {
        if (forcing == null || weather == null) {
            throw new IllegalArgumentException("weather composition components must not be null");
        }
        return new AtmosphericRuntimeComposition(Optional.of(forcing), Optional.of(weather));
    }
}
