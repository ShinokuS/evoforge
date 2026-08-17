package io.github.evoforge.simulation.world.climate;

import io.github.evoforge.simulation.world.mechanics.geometry.CellVolumeRate;
import io.github.evoforge.simulation.world.mechanics.measurement.WaterDepthRate;
import java.math.BigInteger;

/**
 * Versioned long-term atmospheric-water normal used by generated climate facts.
 *
 * <p>The legacy cell-relative form exists only to reproduce V1-V7 worlds authored before a
 * physical space/time contract existed. V8+ uses physical water depth per physical time.</p>
 */
public sealed interface ClimateWaterNormal
        permits ClimateWaterNormal.LegacyCellVolume, ClimateWaterNormal.PhysicalDepth {

    enum Kind {
        LEGACY_CELL_VOLUME_PER_TICK,
        PHYSICAL_WATER_DEPTH_PER_TIME
    }

    Kind kind();

    /** Exact rational numerator for dimension-preserving ratio calculations. */
    BigInteger rateNumerator();

    /** Exact rational denominator for dimension-preserving ratio calculations. */
    BigInteger rateDenominator();

    static ClimateWaterNormal legacy(CellVolumeRate rate) {
        return new LegacyCellVolume(rate);
    }

    static ClimateWaterNormal physical(WaterDepthRate rate) {
        return new PhysicalDepth(rate);
    }

    record LegacyCellVolume(CellVolumeRate rate) implements ClimateWaterNormal {
        public LegacyCellVolume {
            if (rate == null) throw new IllegalArgumentException("legacy climate rate must not be null");
        }

        @Override
        public Kind kind() {
            return Kind.LEGACY_CELL_VOLUME_PER_TICK;
        }

        @Override
        public BigInteger rateNumerator() {
            return BigInteger.valueOf(rate.volumeUnitsNumerator());
        }

        @Override
        public BigInteger rateDenominator() {
            return BigInteger.valueOf(rate.tickDenominator());
        }
    }

    record PhysicalDepth(WaterDepthRate rate) implements ClimateWaterNormal {
        public PhysicalDepth {
            if (rate == null) throw new IllegalArgumentException("physical climate rate must not be null");
        }

        @Override
        public Kind kind() {
            return Kind.PHYSICAL_WATER_DEPTH_PER_TIME;
        }

        @Override
        public BigInteger rateNumerator() {
            return rate.depthNanometersNumerator();
        }

        @Override
        public BigInteger rateDenominator() {
            return rate.durationNanosecondsDenominator();
        }
    }
}
