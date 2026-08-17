package io.github.evoforge.simulation.world.weather.model;

import io.github.evoforge.simulation.time.SimulationTimeScale;
import io.github.evoforge.simulation.world.mechanics.measurement.WaterDepthRate;
import io.github.evoforge.simulation.world.weather.WeatherDriver;
import io.github.evoforge.simulation.world.weather.WeatherFootprint;
import io.github.evoforge.simulation.world.weather.WeatherState;
import java.math.BigInteger;
import java.time.Duration;

/**
 * Deterministic alternating dry/wet rainfall pulse driver.
 *
 * <p>Dry-spell duration, wet-spell duration and wet intensity are sampled independently from
 * exponential distributions around compiled physical means. This mirrors a classic family of
 * stochastic rainfall-occurrence and rectangular-pulse models without embedding climate-specific
 * coefficients in the engine. The supplied parameters are expected to come from calibration, not
 * direct world authoring.</p>
 *
 * <p>The driver owns stochastic process phase only. {@link WeatherState} remains the authoritative
 * owner of current atmospheric values. A footprint gives one coherent local process; a later
 * spatial storm model can replace this driver without changing the weather/Water boundary.</p>
 */
public final class AlternatingRainfallPulseDriver implements WeatherDriver {
    private static final BigInteger BILLION = BigInteger.valueOf(1_000_000_000L);
    private static final BigInteger NANOS_PER_SECOND = BigInteger.valueOf(1_000_000_000L);
    private static final long ORDINAL_SALT = 0x9e3779b97f4a7c15L;
    private static final long LANE_SALT = 0xd1b54a32d192ed03L;
    private static final long DRY_LANE = 0L;
    private static final long WET_DURATION_LANE = 1L;
    private static final long WET_INTENSITY_LANE = 2L;
    private static final double TWO_POW_53 = 0x1.0p53;
    private static final double PARTS_PER_BILLION = 1_000_000_000d;

    private final WeatherState weather;
    private final WeatherFootprint footprint;
    private final RainfallPulseParameters parameters;
    private final SimulationTimeScale timeScale;
    private final long seed;

    private boolean raining;
    private long eventOrdinal;
    private long nextTransitionTick;
    private long lastUpdateTick = -1L;

    public AlternatingRainfallPulseDriver(
            WeatherState weather,
            WeatherFootprint footprint,
            RainfallPulseParameters parameters,
            SimulationTimeScale timeScale,
            long seed) {
        if (weather == null || footprint == null || parameters == null || timeScale == null) {
            throw new IllegalArgumentException("rainfall driver dependencies must not be null");
        }
        footprint.requireInside(weather.bounds());
        this.weather = weather;
        this.footprint = footprint;
        this.parameters = parameters;
        this.timeScale = timeScale;
        this.seed = seed;
        applyPrecipitation(WaterDepthRate.ZERO);
        nextTransitionTick = sampleDurationTicks(
                parameters.meanDrySpellDuration(),
                eventOrdinal,
                DRY_LANE);
    }

    @Override
    public void update(long tick) {
        if (tick < 0L) {
            throw new IllegalArgumentException("weather tick must be non-negative");
        }
        if (tick < lastUpdateTick) {
            throw new IllegalArgumentException("weather driver cannot move backwards in time");
        }

        while (tick >= nextTransitionTick) {
            long transitionTick = nextTransitionTick;
            if (raining) {
                finishWetSpell(transitionTick);
            } else {
                beginWetSpell(transitionTick);
            }
        }
        lastUpdateTick = tick;
    }

    public boolean raining() {
        return raining;
    }

    public long nextTransitionTick() {
        return nextTransitionTick;
    }

    private void beginWetSpell(long transitionTick) {
        long intensityScale = sampleExponentialPartsPerBillion(eventOrdinal, WET_INTENSITY_LANE);
        applyPrecipitation(scale(parameters.meanWetIntensity(), intensityScale));
        raining = true;
        nextTransitionTick = Math.addExact(
                transitionTick,
                sampleDurationTicks(
                        parameters.meanWetSpellDuration(),
                        eventOrdinal,
                        WET_DURATION_LANE));
    }

    private void finishWetSpell(long transitionTick) {
        applyPrecipitation(WaterDepthRate.ZERO);
        raining = false;
        eventOrdinal = Math.incrementExact(eventOrdinal);
        nextTransitionTick = Math.addExact(
                transitionTick,
                sampleDurationTicks(
                        parameters.meanDrySpellDuration(),
                        eventOrdinal,
                        DRY_LANE));
    }

    private long sampleDurationTicks(Duration mean, long ordinal, long lane) {
        BigInteger meanNanoseconds = durationNanoseconds(mean);
        long scale = sampleExponentialPartsPerBillion(ordinal, lane);
        BigInteger sampledNanoseconds = meanNanoseconds
                .multiply(BigInteger.valueOf(scale))
                .divide(BILLION);
        if (sampledNanoseconds.signum() <= 0) {
            sampledNanoseconds = BigInteger.ONE;
        }
        return timeScale.ticksForCeiling(sampledNanoseconds);
    }

    private long sampleExponentialPartsPerBillion(long ordinal, long lane) {
        long mixed = mix64(seed ^ (ordinal * ORDINAL_SALT) ^ (lane * LANE_SALT));
        long mantissa = mixed >>> 11;
        double unit = (mantissa + 1.0d) / TWO_POW_53;
        double exponential = -StrictMath.log(unit);
        long scaled = Math.round(exponential * PARTS_PER_BILLION);
        return Math.max(1L, scaled);
    }

    private void applyPrecipitation(WaterDepthRate rate) {
        for (long y = footprint.minY(); y <= (long) footprint.maxY(); y++) {
            int worldY = (int) y;
            for (long x = footprint.minX(); x <= (long) footprint.maxX(); x++) {
                weather.setPrecipitationRateAt((int) x, worldY, rate);
            }
        }
    }

    private static WaterDepthRate scale(WaterDepthRate mean, long partsPerBillion) {
        return new WaterDepthRate(
                mean.depthNanometersNumerator().multiply(BigInteger.valueOf(partsPerBillion)),
                mean.durationNanosecondsDenominator().multiply(BILLION));
    }

    private static BigInteger durationNanoseconds(Duration duration) {
        return BigInteger.valueOf(duration.getSeconds())
                .multiply(NANOS_PER_SECOND)
                .add(BigInteger.valueOf(duration.getNano()));
    }

    private static long mix64(long value) {
        long mixed = value;
        mixed = (mixed ^ (mixed >>> 30)) * 0xbf58476d1ce4e5b9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94d049bb133111ebL;
        return mixed ^ (mixed >>> 31);
    }
}
