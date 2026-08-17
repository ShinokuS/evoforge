package io.github.evoforge.simulation.world.weather.model;

import io.github.evoforge.simulation.world.calibration.rainfall.RainfallOccurrenceNormal;
import io.github.evoforge.simulation.world.calibration.rainfall.RainfallRegime;
import io.github.evoforge.simulation.world.mechanics.measurement.WaterDepthRate;
import java.math.BigInteger;
import java.time.Duration;

/** Compiles algorithm-independent rainfall statistics for the alternating rectangular-pulse model. */
public final class AlternatingRainfallPulseCompiler {
    private static final BigInteger NANOS_PER_SECOND = BigInteger.valueOf(1_000_000_000L);

    private AlternatingRainfallPulseCompiler() { }

    public static RainfallPulseParameters compile(RainfallRegime regime) {
        if (regime == null) throw new IllegalArgumentException("rainfall regime must not be null");
        WaterDepthRate mean = regime.longTermMeanPrecipitation();
        if (mean.depthNanometersNumerator().signum() <= 0) {
            throw new IllegalArgumentException(
                    "alternating rainfall pulses require positive long-term precipitation");
        }

        RainfallOccurrenceNormal occurrence = regime.occurrence();
        BigInteger dryNanos = durationNanoseconds(occurrence.meanDrySpellDuration());
        BigInteger wetNanos = durationNanoseconds(occurrence.meanWetSpellDuration());
        BigInteger cycleNanos = dryNanos.add(wetNanos);

        WaterDepthRate wetIntensity = new WaterDepthRate(
                mean.depthNanometersNumerator().multiply(cycleNanos),
                mean.durationNanosecondsDenominator().multiply(wetNanos));

        return new RainfallPulseParameters(
                occurrence.meanDrySpellDuration(),
                occurrence.meanWetSpellDuration(),
                wetIntensity);
    }

    private static BigInteger durationNanoseconds(Duration duration) {
        return BigInteger.valueOf(duration.getSeconds())
                .multiply(NANOS_PER_SECOND)
                .add(BigInteger.valueOf(duration.getNano()));
    }
}
